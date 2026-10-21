import { Decimal } from 'decimal.js';

// Keep risk presentation arithmetic independent of any other consumer's configuration.
// Match Java's significant-digit budget; explicitly round scenario cash flows half up.
export const RiskDecimal = Decimal.clone({ precision: 34, rounding: Decimal.ROUND_HALF_UP });
