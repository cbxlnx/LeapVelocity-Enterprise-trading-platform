import { createHash } from "crypto";
import { ConflictException, Injectable, UnauthorizedException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import { UserRepository } from "../repository/user.repository";
import { TokenService } from "./token.service";
import { ThrottleService } from "./throttle.service";
import type { AuthenticatedUser } from "../types/authenticated-user";

const SALT_ROUNDS = 12;

/**
 * Utility function to introduce a delay.
 * Used to prevent timing-based attacks by ensuring consistent response times.
 */
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

@Injectable()
export class AuthService {
  constructor(
    private readonly tokens: TokenService,
    private readonly users: UserRepository,
    private readonly throttle: ThrottleService,
  ) {}

  async register(username: string, password: string): Promise<{ username: string; registered: true }> {
    if (await this.users.findByUsername(username)) {
      throw new ConflictException(`${username} is already registered`);
    }
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    if (!(await this.users.create(username, passwordHash))) {
      throw new ConflictException(`${username} is already registered`);
    }
    return { username, registered: true };
  }

  async login(username: string, password: string): Promise<{ accessToken: string; refreshToken: string }> {
    const startTime = Date.now();
    const responseDelayMs = this.throttle.getResponseDelayMs();

    try {
      // Check throttle status: if account is in cooldown, deny immediately
      if (this.throttle.isThrottled(username)) {
        throw new UnauthorizedException("invalid username or password");
      }

      // Attempt authentication without early returns
      const user = await this.users.findByUsername(username);
      const passwordMatches = user ? await bcrypt.compare(password, user.passwordHash) : false;

      if (!user || !passwordMatches) {
        // Record failed attempt (both paths hit this: missing user or wrong password)
        this.throttle.recordFailedAttempt(username);
        throw new UnauthorizedException("invalid username or password");
      }

      // Success path: issue tokens
      const accessToken = this.tokens.issue(user);
      const refreshToken = this.tokens.issue(user, "refresh");
      await this.users.storeRefreshTokenHash(user.id, this.hashToken(refreshToken));

      // Reset throttle counter on successful login
      this.throttle.resetAttempts(username);

      // Ensure minimum response delay to prevent timing attacks
      const elapsedMs = Date.now() - startTime;
      if (elapsedMs < responseDelayMs) {
        await delay(responseDelayMs - elapsedMs);
      }

      return { accessToken, refreshToken };
    } catch (error) {
      // Ensure failure path also respects minimum response delay
      const elapsedMs = Date.now() - startTime;
      if (elapsedMs < responseDelayMs) {
        await delay(responseDelayMs - elapsedMs);
      }
      throw error;
    }
  }

  async refresh(refreshToken: string): Promise<{ accessToken: string }> {
    const payload = this.tokens.verify(refreshToken, "refresh");
    const user = await this.users.findByRefreshTokenHash(payload.sub, this.hashToken(refreshToken));

    if (!user) {
      throw new UnauthorizedException("invalid or expired refresh token");
    }

    return { accessToken: this.tokens.issue(user) };
  }

  validate(accessToken: string): AuthenticatedUser {
    const payload = this.tokens.verify(accessToken);
    return { username: payload.username };
  }

  private hashToken(token: string): string {
    return createHash("sha256").update(token).digest("hex");
  }
}
