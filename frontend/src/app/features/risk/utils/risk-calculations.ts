import { Decimal } from 'decimal.js';
import { Instrument } from '../../../models/api.models';
import { PortfolioRisk } from '../../analytics/models/portfolio-risk.model';
import { DrawdownPoint, RiskPortfolio, ShockResult, ShockValues } from '../models/risk-dashboard.model';
import { RiskDecimal } from './risk-decimal';

export function formatAmount(value: number | null | undefined, prefix = '', suffix = ''): string {
  return value == null || !Number.isFinite(value) ? 'Unavailable'
    : `${prefix}${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${suffix}`;
}

/** Draw supplied wealth relative to its running peak; no new return model. */
export function drawdownPoints(assessment: PortfolioRisk): DrawdownPoint[] {
  if (assessment.series.status !== 'READY') return [];
  const wealth = assessment.series.wealth;
  if (!wealth.length || wealth[0].value <= 0 || wealth.some(p => !Number.isFinite(p.value) || p.value < 0)) return [];
  let peak = wealth[0].value;
  return wealth.map(point => {
    peak = Math.max(peak, point.value);
    return { date: point.date, percent: (point.value / peak - 1) * 100 };
  });
}

export function toRiskPortfolio(assessment: PortfolioRisk, instruments: Instrument[] | null): RiskPortfolio {
  const valuation = assessment.valuation;
  const ready = valuation.valuationStatus === 'READY'
    && !assessment.issues.some(issue => issue.scope === 'valuation' && issue.severity === 'ERROR');
  const invested = ready ? valuation.holdingsValue : valuation.valuationStatus === 'EMPTY' ? 0 : null;
  const total = ready || valuation.valuationStatus === 'EMPTY' ? valuation.totalEquity : null;
  const metadata = new Map(instruments?.map(instrument => [instrument.symbol, instrument]));
  const holdings = valuation.holdings.map(holding => {
    const instrument = metadata.get(holding.symbol);
    return {
      symbol: holding.symbol, company: instrument?.name ?? null, instrumentType: instrument?.assetClass ?? null,
      quantity: holding.quantity, marketValue: ready ? holding.marketValue : null,
      allocationPercent: ready && invested != null && invested > 0 && holding.marketValue != null
        ? new RiskDecimal(holding.marketValue).div(invested).times(100).toNumber() : null,
    };
  }).sort((a, b) => (b.marketValue ?? -Infinity) - (a.marketValue ?? -Infinity) || a.symbol.localeCompare(b.symbol));
  const largest = holdings[0];
  const groups = new Map<string, Decimal>();
  const grouped = ready && holdings.every(holding => holding.instrumentType && holding.marketValue != null);
  if (grouped) holdings.forEach(holding => groups.set(holding.instrumentType!,
    new RiskDecimal(groups.get(holding.instrumentType!) ?? 0).plus(holding.marketValue!)));
  return {
    accountId: valuation.accountId, asOf: valuation.asOf, valuationReady: ready,
    cashReserveUsd: valuation.cashBalance, investedEquityUsd: invested, portfolioValueUsd: total, holdings,
    drawdownSeries: drawdownPoints(assessment),
    instrumentExposure: grouped && invested != null && invested > 0
      ? [...groups].map(([name, marketValue]) => ({ name, marketValue: marketValue.toNumber(),
        allocationPercent: marketValue.div(invested).times(100).toNumber() })) : null,
    insights: [
      { label: 'Largest holding exposure', value: largest?.allocationPercent != null ? `${largest.symbol} · ${formatAmount(largest.allocationPercent, '', '%')}` : 'Unavailable',
        detail: valuation.valuationStatus === 'EMPTY' ? 'No invested holdings' : 'Percentage of invested holdings, excluding cash' },
      { label: 'Sector exposure', value: 'Unavailable', detail: 'Instrument metadata has no sector classification' },
      { label: 'Cash reserve', value: formatAmount(valuation.cashBalance, '$'),
        detail: total != null && total > 0 ? `${formatAmount(new RiskDecimal(valuation.cashBalance).div(total).times(100).toNumber(), '', '%')} of total account equity` : 'Account cash; equity percentage unavailable' },
      { label: 'Historical data coverage', value: `${assessment.history.returnObservations} daily returns`,
        detail: assessment.series.status === 'READY' ? `${assessment.history.from} – ${assessment.history.to} · Validated common sessions` : assessment.series.reasons.join(' · ') },
    ],
    assessment,
  };
}

export function roundCurrency(value: Decimal.Value): number {
  return new RiskDecimal(value).toDecimalPlaces(2).toNumber();
}

/** Existing uniform price shock: apply the user's percentage once per holding; cash unchanged. */
export function calculateCustomShock(portfolio: RiskPortfolio, shocks: ShockValues): ShockResult | null {
  if (!portfolio.valuationReady || portfolio.portfolioValueUsd == null || portfolio.cashReserveUsd == null
      || !Number.isFinite(portfolio.portfolioValueUsd) || !Number.isFinite(portfolio.cashReserveUsd)
      || !portfolio.holdings.length || !Number.isFinite(shocks.market) || Math.abs(shocks.market) > 50
      || portfolio.holdings.some(holding => holding.marketValue == null || !Number.isFinite(holding.marketValue))) return null;
  const deltas = portfolio.holdings.map(holding => ({ label: holding.symbol,
    deltaUsd: roundCurrency(new RiskDecimal(holding.marketValue!).times(shocks.market).div(100)) }));
  const pnl = RiskDecimal.sum(...deltas.map(delta => delta.deltaUsd));
  const lossUsd = pnl.negated().toNumber();
  const postShockEquityUsd = roundCurrency(new RiskDecimal(portfolio.portfolioValueUsd).plus(pnl));
  return { lossUsd, postShockEquityUsd,
    impactPercent: portfolio.portfolioValueUsd > 0 ? new RiskDecimal(lossUsd).div(portfolio.portfolioValueUsd).times(100).toNumber() : null,
    waterfall: { title: 'Custom uniform price shock', period: `Valuation ${portfolio.asOf} · Price change ${shocks.market}%`,
      startingEquityUsd: portfolio.portfolioValueUsd, endingEquityUsd: postShockEquityUsd,
      cashReserveUsd: portfolio.cashReserveUsd, deltas },
  };
}
