import { UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { JwtService } from "@nestjs/jwt";
import { TokenService } from "../service/token.service";

const secret = "token-unit-test-secret-at-least-64-bytes-abcdefghijklmnopqrstuvwxy";
const issuer = "leapvelocity-auth";
const jwt = new JwtService({ secret, signOptions: { issuer, algorithm: "HS256" } });
const tokens = new TokenService(jwt, new ConfigService({ JWT_EXPIRES_IN: "15m", REFRESH_TOKEN_EXPIRES_IN: "2d" }));
const user = { id: "42", username: "alice", accountId: 7 };

describe("TokenService", () => {
  it("issues minimal signed tokens with configured lifetimes", () => {
    const access = tokens.verify(tokens.issue(user));
    const refresh = tokens.verify(tokens.issue(user, "refresh"), "refresh");
    expect(access).toMatchObject({ sub: "42", username: "alice", accountId: 7, iss: issuer, roles: ["TRADER"] });
    expect(access.exp - access.iat!).toBe(15 * 60);
    expect(refresh.exp - refresh.iat!).toBe(2 * 24 * 60 * 60);
    expect(access).not.toHaveProperty("password");
    expect(access).not.toHaveProperty("passwordHash");
    expect(access.jti).not.toBe(refresh.jti);
  });

  it("keeps decode for inspection and rejects unverified claims", () => {
    const tampered = jwt.sign({ sub: "42", username: "alice" }, { secret: "wrong-secret-at-least-32-bytes-long", expiresIn: "1h" });
    expect(tokens.decode(tampered)).toMatchObject({ sub: "42" });
    expect(() => tokens.verify(tampered)).toThrow(UnauthorizedException);
  });

  it.each([
    ["expired", {}, { expiresIn: -1 }],
    ["wrong issuer", {}, { issuer: "other", expiresIn: "1h" }],
    ["wrong algorithm", {}, { algorithm: "HS384" as const, expiresIn: "1h" }],
    ["future token", {}, { notBefore: "1h", expiresIn: "2h" }],
    ["missing expiry", {}, {}],
    ["missing subject", { sub: undefined }, { expiresIn: "1h" }],
    ["missing username", { username: undefined }, { expiresIn: "1h" }],
    ["blank username", { username: " " }, { expiresIn: "1h" }],
    ["refresh type", { type: "refresh" }, { expiresIn: "1h" }],
  ] as const)("rejects %s access tokens", (_name, claims, options) => {
    const token = jwt.sign({ sub: "42", username: "alice", ...claims }, options);
    expect(() => tokens.verify(token)).toThrow(UnauthorizedException);
  });

  it("requires refresh type and expiration even for correctly signed refresh credentials", () => {
    expect(() => tokens.verify(tokens.issue(user), "refresh")).toThrow(UnauthorizedException);
    expect(() => tokens.verify(jwt.sign({ sub: "42", username: "alice", type: "refresh" }), "refresh"))
      .toThrow(UnauthorizedException);
    expect(() => tokens.verify("invalid", "refresh")).toThrow(UnauthorizedException);
  });
});
