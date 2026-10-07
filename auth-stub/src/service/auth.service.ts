import { createHash } from "crypto";
import { ConflictException, Injectable, UnauthorizedException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import { UserRepository } from "../repository/user.repository";
import { TokenService } from "./token.service";
import type { AuthenticatedUser } from "../types/authenticated-user";

const SALT_ROUNDS = 12;

@Injectable()
export class AuthService {
  constructor(
    private readonly tokens: TokenService,
    private readonly users: UserRepository,
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
    const user = await this.users.findByUsername(username);
    const passwordMatches = user ? await bcrypt.compare(password, user.passwordHash) : false;
    if (!user || !passwordMatches) {
      throw new UnauthorizedException("invalid username or password");
    }
    const accessToken = this.tokens.issue(user);
    const refreshToken = this.tokens.issue(user, "refresh");
    await this.users.storeRefreshTokenHash(user.id, this.hashToken(refreshToken));
    return { accessToken, refreshToken };
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
