package com.leapvelocity.service;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Real Python HTTP service shared by integration tests; can also target an isolated Docker container. */
final class PythonRiskServer {
    static final String TOKEN = "risk-integration-test-token";
    private static final String URL = start();
    static RiskCalculationClient client() { return new RiskCalculationClient(URL, TOKEN, 1000, 5000); }

    private static String start() {
        String external = System.getenv("RISK_TEST_URL");
        if (external != null) return external;
        try {
            Path root = Path.of("..").toAbsolutePath().normalize();
            String python = System.getenv("RISK_TEST_PYTHON");
            if (python == null) python = Files.exists(root.resolve(".venv/bin/python"))
                    ? root.resolve(".venv/bin/python").toString() : "python3";
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            var builder = new ProcessBuilder(python, "-m", "uvicorn", "analysis.risk_service.app:app",
                    "--host", "127.0.0.1", "--port", Integer.toString(port), "--log-level", "warning")
                    .directory(root.toFile()).redirectErrorStream(true)
                    .redirectOutput(root.resolve("backend/target/python-risk-test.log").toFile());
            builder.environment().put("RISK_SERVICE_TOKEN", TOKEN);
            var process = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
            String url = "http://127.0.0.1:" + port;
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
            for (int attempt = 0; attempt < 100; attempt++) {
                if (!process.isAlive()) break;
                try {
                    if (http.send(HttpRequest.newBuilder(URI.create(url + "/health")).timeout(Duration.ofMillis(200))
                            .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return url;
                } catch (Exception ignored) { /* Startup only; bounded polling. */ }
                Thread.sleep(100);
            }
            process.destroy();
            throw new IllegalStateException("Python risk API did not start. Install analysis/requirements-dev.txt; see target/python-risk-test.log");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot start Python risk integration service", exception);
        }
    }
}
