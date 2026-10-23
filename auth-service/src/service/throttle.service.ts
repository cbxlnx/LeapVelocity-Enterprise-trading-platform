import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import {
  DEFAULT_MAX_FAILED_ATTEMPTS,
  DEFAULT_THROTTLE_COOLDOWN_MS,
  DEFAULT_RESPONSE_DELAY_MS,
} from "../config/throttle.config";

interface AttemptRecord {
  failedCount: number;
  throttledUntil: number | null;
}

@Injectable()
export class ThrottleService {
  private readonly maxFailedAttempts: number;
  private readonly cooldownMs: number;
  private readonly responseDelayMs: number;
  private readonly attempts = new Map<string, AttemptRecord>();

  constructor(configService: ConfigService) {
    this.maxFailedAttempts = parseInt(
      configService.get<string>("LOGIN_THROTTLE_MAX_ATTEMPTS") ?? String(DEFAULT_MAX_FAILED_ATTEMPTS),
      10,
    );
    this.cooldownMs = parseInt(
      configService.get<string>("LOGIN_THROTTLE_COOLDOWN_MS") ?? String(DEFAULT_THROTTLE_COOLDOWN_MS),
      10,
    );
    this.responseDelayMs = parseInt(
      configService.get<string>("LOGIN_THROTTLE_RESPONSE_DELAY_MS") ?? String(DEFAULT_RESPONSE_DELAY_MS),
      10,
    );
  }

  isThrottled(username: string): boolean {
    const record = this.attempts.get(username);
    if (!record) return false;
    if (record.throttledUntil !== null && Date.now() < record.throttledUntil) return true;
    if (record.throttledUntil !== null && Date.now() >= record.throttledUntil) {
      this.attempts.delete(username);
    }
    return false;
  }

  recordFailedAttempt(username: string): void {
    const record = this.attempts.get(username) ?? { failedCount: 0, throttledUntil: null };
    record.failedCount += 1;
    if (record.failedCount >= this.maxFailedAttempts) {
      record.throttledUntil = Date.now() + this.cooldownMs;
    }
    this.attempts.set(username, record);
  }

  resetAttempts(username: string): void {
    this.attempts.delete(username);
  }

  getResponseDelayMs(): number {
    return this.responseDelayMs;
  }
}
