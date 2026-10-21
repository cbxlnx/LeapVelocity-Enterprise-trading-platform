package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.exceptions.RiskCalculationException;
import com.leapvelocity.service.PortfolioReturnPreparation.Availability;
import com.leapvelocity.service.PortfolioReturnPreparation.PreparedInputs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.leapvelocity.exceptions.RiskCalculationException.Kind.*;

/** Bounded, authenticated internal HTTP boundary; no fallback risk formulas. */
@Component
public class RiskCalculationClient {
    private final HttpClient http;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).build();

    public RiskCalculationClient(@Value("${app.risk-service.url:http://localhost:8000}") String url,
                                 @Value("${app.risk-service.token:}") String token,
                                 @Value("${app.risk-service.connect-timeout-ms:1000}") int connectMs,
                                 @Value("${app.risk-service.read-timeout-ms:5000}") int readMs) {
        URI base = URI.create(url);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("Risk service URL must be an HTTP(S) origin");
        }
        if (token == null || token.isBlank() || token.chars().anyMatch(value -> value < 33 || value > 126)) {
            throw new IllegalArgumentException("RISK_SERVICE_TOKEN is required");
        }
        if (connectMs < 1 || connectMs > 10000 || readMs < 1 || readMs > 30000) {
            throw new IllegalArgumentException("Risk timeouts must be 1–10000 ms connect and 1–30000 ms read");
        }
        this.endpoint = base.resolve("/internal/v1/risk/calculate");
        this.token = token;
        this.timeout = Duration.ofMillis(readMs);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectMs))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public RiskCalculationContract.Response calculate(PreparedInputs inputs) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Risk HTTP calculation must run after the snapshot transaction completes");
        }
        String id = UUID.randomUUID().toString();
        var body = RiskCalculationContract.Request.from(inputs, id);
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-Risk-Service-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // The future completes only after the body is consumed. A request timeout
            // alone may finish at response headers and leave a stalled body unbounded.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new RiskCalculationException(UNAVAILABLE, null);
            if (response.body().length() > 1_000_000 || !response.headers().firstValue("Content-Type")
                    .orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                throw new RiskCalculationException(INVALID_RESPONSE, null);
            }
            try {
                var tree = json.readTree(response.body());
                for (String name : List.of("annualisedVolatility", "maximumDrawdown", "beta")) {
                    var number = tree.at("/metrics/" + name + "/value");
                    require(number.isNull() || number.isString());
                }
                for (var value : tree.path("valueAtRisk")) {
                    require(value.path("lossPercent").isNull() || value.path("lossPercent").isString());
                    require(value.path("lossAmount").isNull() || value.path("lossAmount").isString());
                }
                for (String name : List.of("confidenceLevel", "varAmount", "expectedShortfallAmount", "expectedShortfallPercent")) {
                    var value = tree.at("/monteCarlo/" + name);
                    require(value.isNull() || value.isString());
                }
                for (var bin : tree.at("/monteCarlo/bins")) {
                    require(bin.path("lowerBound").isString() && bin.path("upperBound").isString());
                }
                var result = json.readValue(response.body(), RiskCalculationContract.Response.class);
                validate(result, inputs, id);
                return result;
            } catch (RuntimeException ex) {
                throw new RiskCalculationException(INVALID_RESPONSE, ex);
            }
        } catch (TimeoutException ex) {
            pending.cancel(true);
            throw new RiskCalculationException(TIMEOUT, ex);
        } catch (InterruptedException ex) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new RiskCalculationException(UNAVAILABLE, ex);
        } catch (ExecutionException ex) {
            throw new RiskCalculationException(ex.getCause() instanceof HttpTimeoutException ? TIMEOUT : UNAVAILABLE, ex.getCause());
        }
    }

    private static void validate(RiskCalculationContract.Response response, PreparedInputs input, String id) {
        require(response != null && "1.0".equals(response.schemaVersion()) && id.equals(response.requestId()));
        var returns = input.portfolio().returns();
        var pairs = input.pairedReturns();
        LocalDate from = returns.isEmpty() ? null : returns.get(0).from();
        LocalDate to = returns.isEmpty() ? null : returns.get(returns.size() - 1).to();
        var metrics = Objects.requireNonNull(response.metrics());
        scalar(metrics.annualisedVolatility(), input.core(), returns.size(), Unit.PERCENT, from, to, true);
        scalar(metrics.maximumDrawdown(), input.core(), returns.size(), Unit.PERCENT, from, to, true);
        if (metrics.maximumDrawdown().value() != null) require(metrics.maximumDrawdown().value().compareTo(new BigDecimal("100")) <= 0);
        scalar(metrics.beta(), input.beta(), pairs.size(), Unit.DIMENSIONLESS,
                pairs.isEmpty() ? null : pairs.get(0).from(), pairs.isEmpty() ? null : pairs.get(pairs.size() - 1).to(), false);
        var vars = Objects.requireNonNull(response.valueAtRisk());
        require(vars.size() == 3);
        int count = Math.max(0, returns.size() - input.parameters().holdingPeriodDays() + 1);
        var methods = List.of(VarMethod.HISTORICAL, VarMethod.PARAMETRIC, VarMethod.MONTE_CARLO);
        for (int index = 0; index < methods.size(); index++) {
            var value = vars.get(index);
            require(value != null && value.method() == methods.get(index) && value.estimate());
            context(value.status(), value.observations(), value.requiredObservations(), value.from(), value.to(), value.reasons(),
                    input.historicalVar(), count, PortfolioReturnPreparation.minimumVarObservations(input.parameters().confidenceLevel()),
                    count == 0 ? null : from, count == 0 ? null : to);
            number(value.lossPercent(), value.status(), 8, true);
            number(value.lossAmount(), value.status(), 2, true);
        }
        require(response.issues() != null);
        distribution(response.monteCarlo(), vars.get(2), input);
        for (var issue : response.issues()) {
            require(issue != null && issue.code() != null && issue.severity() == Severity.WARNING
                    && issue.scope() != null && issue.message() != null && issue.symbol() == null);
            List<String> scopes = List.of("metrics.annualisedVolatility", "metrics.maximumDrawdown", "metrics.beta",
                    "valueAtRisk.HISTORICAL", "valueAtRisk.PARAMETRIC", "valueAtRisk.MONTE_CARLO", "monteCarlo");
            require(scopes.contains(issue.scope()));
        }
    }

    private static void distribution(MonteCarloDistribution value, VarResult var, PreparedInputs input) {
        require(value != null && value.status() != null && value.reasons() != null && value.bins() != null);
        require(value.simulations() == 100_000 && value.confidenceLevel() != null
                && value.confidenceLevel().compareTo(input.parameters().confidenceLevel()) == 0
                && value.holdingPeriodDays() == input.parameters().holdingPeriodDays()
                && "USD_LOSS".equals(value.unit()) && "EMPIRICAL_INTEGRATED_QUANTILE".equals(value.tailConvention()));
        number(value.varAmount(), value.status(), 2, true);
        number(value.expectedShortfallAmount(), value.status(), 2, true);
        number(value.expectedShortfallPercent(), value.status(), 8, true);
        if (value.status() != ResultStatus.READY) {
            require(value.status() == ResultStatus.EMPTY || value.status() == ResultStatus.UNAVAILABLE);
            require(value.bins().isEmpty() && !value.reasons().isEmpty());
            if (var.status() != ResultStatus.READY) {
                require(value.status() == var.status() && value.reasons().equals(var.reasons()));
            } else require(value.status() == ResultStatus.UNAVAILABLE && value.reasons().equals(List.of(IssueCode.NUMERICAL_ERROR)));
            return;
        }
        require(var.status() == ResultStatus.READY && value.reasons().isEmpty()
                && value.varAmount().compareTo(var.lossAmount()) == 0
                && value.expectedShortfallAmount().compareTo(var.lossAmount()) >= 0
                && value.expectedShortfallPercent().compareTo(var.lossPercent()) >= 0);
        require(!value.bins().isEmpty() && value.bins().size() <= 40);
        long count = 0;
        BigDecimal previous = null;
        for (var bin : value.bins()) {
            require(bin != null && bin.lowerBound() != null && bin.upperBound() != null && bin.count() >= 0);
            require(Double.isFinite(bin.lowerBound().doubleValue()) && Double.isFinite(bin.upperBound().doubleValue()));
            require(bin.lowerBound().compareTo(bin.upperBound()) <= (value.bins().size() == 1 ? 0 : -1));
            require(previous == null || previous.compareTo(bin.lowerBound()) == 0);
            previous = bin.upperBound();
            count += bin.count();
        }
        require(count == value.simulations());
    }

    private static void scalar(ScalarMetric value, Availability eligibility, int count, Unit unit,
                               LocalDate from, LocalDate to, boolean nonnegative) {
        require(value != null && value.unit() == unit);
        context(value.status(), value.observations(), value.requiredObservations(), value.from(), value.to(), value.reasons(),
                eligibility, count, Math.max(60, eligibility.requiredObservations()), from, to);
        number(value.value(), value.status(), 8, nonnegative);
    }

    private static void context(ResultStatus status, int count, int required, LocalDate from, LocalDate to,
                                List<IssueCode> reasons, Availability eligibility, int expectedCount, int expectedRequired,
                                LocalDate expectedFrom, LocalDate expectedTo) {
        require(status != null && status != ResultStatus.NOT_IMPLEMENTED && reasons != null && reasons.stream().allMatch(Objects::nonNull));
        require(count == expectedCount && required == expectedRequired
                && Objects.equals(from, expectedFrom) && Objects.equals(to, expectedTo));
        if (eligibility.status() != ResultStatus.READY) {
            require(status == eligibility.status() && reasons.equals(eligibility.reasons()));
        } else {
            require(status == ResultStatus.READY ? reasons.isEmpty() : status == ResultStatus.UNAVAILABLE && !reasons.isEmpty());
        }
    }

    private static void number(BigDecimal value, ResultStatus status, int scale, boolean nonnegative) {
        if (status != ResultStatus.READY) { require(value == null); return; }
        require(value != null && Double.isFinite(value.doubleValue()) && (!nonnegative || value.signum() >= 0));
        require(value.scale() == scale);
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalStateException("Invalid risk calculation response"); }
}
