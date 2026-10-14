/** Trading dates are calendar dates, not instants in the browser's timezone. */
export function formatTradingDate(value: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`));
}

export function defaultHistoryRange(now = new Date()): { from: string; to: string } {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const part = (type: string) => Number(parts.find((item) => item.type === type)?.value);
  const end = new Date(Date.UTC(part('year'), part('month') - 1, part('day') - 1));
  const start = new Date(end);
  start.setUTCFullYear(start.getUTCFullYear() - 1);
  // Clamp February 29 to February 28 instead of rolling into March.
  if (start.getUTCMonth() !== end.getUTCMonth()) start.setUTCDate(0);
  return { from: start.toISOString().slice(0, 10), to: end.toISOString().slice(0, 10) };
}
