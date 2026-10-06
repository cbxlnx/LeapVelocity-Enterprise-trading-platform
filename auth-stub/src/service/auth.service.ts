import { randomUUID } from "crypto";
import { ConflictException, Injectable, OnModuleInit, UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { JwtService } from "@nestjs/jwt";
import * as bcrypt from "bcrypt";
import type { SignOptions } from "jsonwebtoken";
import {
  DEFAULT_ACCESS_TOKEN_EXPIRES_IN,
  DEFAULT_REFRESH_TOKEN_EXPIRES_IN,
} from "../config/jwt.config";

interface StoredUser {
  passwordHash: string;
  roles: string[];
  refreshToken: string | null;
}

const SALT_ROUNDS = 12;

@Injectable()
export class AuthService implements OnModuleInit {
  private readonly users = new Map<string, StoredUser>();

  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.seedUser("dave", "mission123", ["TRADER"]);
  }

  async register(username: string, password: string): Promise<{ username: string; registered: true }> {
    if (this.users.has(username)) {
      throw new ConflictException(`${username} is already registered`);
    }
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    this.users.set(username, { passwordHash, roles: ["TRADER"], refreshToken: null });
    return { username, registered: true };
  }

  async login(username: string, password: string): Promise<{ accessToken: string; refreshToken: string }> {
    const user = this.users.get(username);
    const passwordMatches = user ? await bcrypt.compare(password, user.passwordHash) : false;
    if (!user || !passwordMatches) {
      throw new UnauthorizedException("invalid username or password");
    }
    const accessToken = this.issueAccessToken(username, user.roles);
    const refreshToken = this.issueRefreshToken(username, user.roles);
    user.refreshToken = refreshToken;
    return { accessToken, refreshToken };
  }

  refresh(refreshToken: string): { accessToken: string } {
    const entry = this.findByRefreshToken(refreshToken);
    if (!entry) {
      throw new UnauthorizedException("invalid or expired refresh token");
    }
    const [username, user] = entry;
    return { accessToken: this.issueAccessToken(username, user.roles) };
  }

  private findByRefreshToken(refreshToken: string): [string, StoredUser] | undefined {
    return [...this.users.entries()].find(([, u]) => u.refreshToken === refreshToken);
  }

  private async seedUser(username: string, password: string, roles: string[]): Promise<void> {
    if (this.users.has(username)) {
      return;
    }

    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    this.users.set(username, { passwordHash, roles, refreshToken: null });
  }

  private issueAccessToken(username: string, roles: string[]): string {
    return this.jwtService.sign(
      { sub: username, roles },
      {
        expiresIn: this.accessTokenExpiresIn,
        jwtid: randomUUID(),
      },
    );
  }

  private issueRefreshToken(username: string, roles: string[]): string {
    return this.jwtService.sign(
      { sub: username, roles, type: "refresh" },
      {
        expiresIn: this.refreshTokenExpiresIn,
        jwtid: randomUUID(),
      },
    );
  }

  private get accessTokenExpiresIn(): SignOptions["expiresIn"] {
    return this.configService.get<SignOptions["expiresIn"]>("JWT_EXPIRES_IN") ?? DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
  }

  private get refreshTokenExpiresIn(): SignOptions["expiresIn"] {
    return this.configService.get<SignOptions["expiresIn"]>("REFRESH_TOKEN_EXPIRES_IN") ?? DEFAULT_REFRESH_TOKEN_EXPIRES_IN;
  }
}
