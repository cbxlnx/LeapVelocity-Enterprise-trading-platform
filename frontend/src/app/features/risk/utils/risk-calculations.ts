import { Decimal } from 'decimal.js';
import { Instrument } from '../../../models/api.models';
import { HistoricalReplay, PortfolioRisk, PortfolioShock } from '../../analytics/models/portfolio-risk.model';
import { DrawdownPoint, RiskPortfolio, ScenarioBreakdown, ShockResult } from '../models/risk-dashboard.model';
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

/** Map the authoritative server result to a chart without recalculating P&L. */
export function toShockResult(response: PortfolioShock): ShockResult | null {
  if (response.status !== 'READY' || response.totalPnL == null || response.postShockEquity == null
      || response.valuation.totalEquity == null || response.valuation.cashBalance == null) return null;
  return { ...response,
    waterfall: { title: 'Custom uniform price shock',
      period: `Valuation ${response.valuation.asOf} · Price change ${response.priceChangePercent}%`,
      startingEquityUsd: response.valuation.totalEquity, endingEquityUsd: response.postShockEquity,
      cashReserveUsd: response.valuation.cashBalance,
      deltas: response.holdings.map(holding => ({ label: holding.symbol, deltaUsd: holding.pnl })) },
  };
}

/** Presentation only: all returns, money and totals are supplied by Java. */
export function toReplayBreakdown(response: HistoricalReplay): ScenarioBreakdown | null {
  if (response.status !== 'READY' || response.totalPnL == null || response.postShockEquity == null
      || response.valuation.totalEquity == null || response.valuation.cashBalance == null
      || response.holdings.length !== response.expectedHoldings || response.validatedHoldings !== response.expectedHoldings
      || response.holdings.some(row => row.status !== 'READY' || row.pnl == null || row.postShockValue == null || row.priceChangePercent == null)) return null;
  return { ...response,
    holdings: response.holdings.map(row => ({ symbol: row.symbol, marketValue: row.marketValue,
      priceChangePercent: row.priceChangePercent!, pnl: row.pnl!, postShockValue: row.postShockValue! })),
    waterfall: { title: response.scenario.title,
      period: `${response.scenario.from} – ${response.scenario.to} · Cumulative historical return on current exposures`,
      startingEquityUsd: response.valuation.totalEquity, endingEquityUsd: response.postShockEquity,
      cashReserveUsd: response.valuation.cashBalance,
      deltas: response.holdings.map(row => ({ label: row.symbol, deltaUsd: row.pnl! })) },
  };
}
