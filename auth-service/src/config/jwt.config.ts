import type { SignOptions } from "jsonwebtoken";

export const DEFAULT_JWT_ISSUER = "leapvelocity-auth";
export const DEFAULT_ACCESS_TOKEN_EXPIRES_IN: SignOptions["expiresIn"] = "1h";
export const DEFAULT_REFRESH_TOKEN_EXPIRES_IN: SignOptions["expiresIn"] = "7d";
