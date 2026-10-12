/**
 * Login Throttle Configuration
 *
 * Prevents brute-force and username enumeration attacks by:
 * 1. Tracking failed login attempts per username
 * 2. Blocking further attempts after MAX_FAILED_ATTEMPTS
 * 3. Enforcing a COOLDOWN_MS period before retry
 * 4. Adding response delay to prevent timing attacks
 *
 * All values can be configured via environment variables:
 * - LOGIN_THROTTLE_MAX_ATTEMPTS (default: 5)
 * - LOGIN_THROTTLE_COOLDOWN_MS (default: 15000)
 * - LOGIN_THROTTLE_RESPONSE_DELAY_MS (default: 100)
 */

/** Maximum failed login attempts before throttle activates */
export const DEFAULT_MAX_FAILED_ATTEMPTS = 5;

/** Cooldown period in milliseconds after max attempts reached */
export const DEFAULT_THROTTLE_COOLDOWN_MS = 15_000; // 15 seconds

/** Minimum response delay in milliseconds to prevent timing attacks */
export const DEFAULT_RESPONSE_DELAY_MS = 100; // 100ms

