import { Injectable, UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { JwtService } from "@nestjs/jwt";
import { randomUUID } from "node:crypto";
import type { JwtPayload, SignOptions } from "jsonwebtoken";
import { DEFAULT_ACCESS_TOKEN_EXPIRES_IN, DEFAULT_REFRESH_TOKEN_EXPIRES_IN, DEFAULT_JWT_ISSUER } from "../config/jwt.config";

export interface VerifiedToken extends JwtPayload {
  sub: string;
  username: string;
  accountId?: number;
  exp: number;
}

@Injectable()
export class TokenService {
  constructor(private readonly jwt: JwtService, private readonly config: ConfigService) {}

  issue(user: { id: string; username: string; accountId: number | null }, type: "access" | "refresh" = "access"): string {
    const expiresIn = type === "access"
      ? this.config.get<SignOptions["expiresIn"]>("JWT_EXPIRES_IN") ?? DEFAULT_ACCESS_TOKEN_EXPIRES_IN
      : this.config.get<SignOptions["expiresIn"]>("REFRESH_TOKEN_EXPIRES_IN") ?? DEFAULT_REFRESH_TOKEN_EXPIRES_IN;
    return this.jwt.sign(
      // Preserve the existing TRADER claim; no role hierarchy is introduced.
      {
        sub: user.id,
        username: user.username,
        accountId: user.accountId ?? undefined,
        roles: ["TRADER"],
        ...(type === "refresh" ? { type } : {}),
      },
      { algorithm: "HS256", issuer: this.issuer, expiresIn, jwtid: randomUUID() },
    );
  }

  verify(token: string, type: "access" | "refresh" = "access"): VerifiedToken {
    try {
      const payload = this.jwt.verify<VerifiedToken>(token, { algorithms: ["HS256"], issuer: this.issuer });
      if (
        !payload || typeof payload !== "object" ||
        typeof payload.sub !== "string" || !payload.sub.trim() ||
        typeof payload.username !== "string" || !payload.username.trim() ||
        (payload.accountId !== undefined && (!Number.isInteger(payload.accountId) || payload.accountId < 1)) ||
        typeof payload.exp !== "number" || !Number.isFinite(payload.exp) ||
        payload.type !== (type === "refresh" ? "refresh" : undefined)
      ) {
        throw new Error("Invalid token claims");
      }
      return payload;
    } catch {
      throw new UnauthorizedException(`invalid or expired ${type} token`);
    }
  }

  // Inspection only: authorization always uses verify().
  decode(token: string): JwtPayload | null {
    return this.jwt.decode<JwtPayload>(token);
  }

  private get issuer(): string {
    return this.config.get<string>("JWT_ISSUER") ?? DEFAULT_JWT_ISSUER;
  }
}
