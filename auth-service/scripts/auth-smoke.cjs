// Run against both services and an expendable test account/database.
// The script registers one identity and reads existing Spring account routes.
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { TokenService } = require("../dist/service/token.service");
const { JwtService } = require("@nestjs/jwt");
const { ConfigService } = require("@nestjs/config");

const authUrl = process.env.AUTH_URL ?? "http://localhost:4000";
const tradeUrl = process.env.TRADE_URL ?? "http://localhost:8081";
const accountId = process.env.SMOKE_ACCOUNT_ID ?? "1";
const secret = process.env.JWT_SECRET;
if (!secret) throw new Error("JWT_SECRET is required for signed negative test cases");
const issuer = process.env.JWT_ISSUER ?? "leapvelocity-auth";
let step = "configuration";

async function request(base, path, status, options = {}) {
  step = `${options.method ?? "GET"} ${base}${path} (expected ${status})`;
  const response = await fetch(base + path, { ...options, signal: AbortSignal.timeout(15000) });
  assert.equal(response.status, status, `${path}: expected ${status}, received ${response.status}`);
  return response.headers.get("content-type")?.includes("application/json") ? response.json() : undefined;
}
function post(body) {
  return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}
function bearer(token) { return { headers: { Authorization: `Bearer ${token}` } }; }

async function main() {
  const username = `sprint-${randomUUID()}`;
  const password = randomUUID();
  await request(authUrl, "/health", 200);
  const registered = await request(authUrl, "/auth/register", 201, post({ username, password }));
  assert.deepEqual(registered, { username, registered: true });
  await request(authUrl, "/auth/register", 409, post({ username, password }));
  await request(authUrl, "/auth/login", 401, post({ username, password: "wrong-password" }));
  await request(authUrl, "/auth/register", 400, post({ username: " ", password }));
  const login = await request(authUrl, "/auth/login", 200, post({ username, password }));

  const jwt = new JwtService({ secret, signOptions: { issuer, algorithm: "HS256" } });
  const tokens = new TokenService(jwt, new ConfigService({ JWT_ISSUER: issuer }));
  const claims = tokens.verify(login.accessToken);
  assert.equal(claims.username, username);
  assert.match(claims.sub, /^\d+$/);
  assert.equal(claims.passwordHash, undefined);
  assert.equal(claims.password, undefined);
  await request(authUrl, "/auth/me", 200, bearer(login.accessToken));
  const path = `/api/v1/accounts/${encodeURIComponent(accountId)}/balance`;
  const invalidTokens = [
    ["missing", undefined],
    ["malformed", "not-a-jwt"],
    ["refresh", login.refreshToken],
    ["expired", new TokenService(jwt, new ConfigService({ JWT_EXPIRES_IN: -1, JWT_ISSUER: issuer })).issue({ id: claims.sub, username })],
    ["wrong signature", jwt.sign({ sub: claims.sub, username }, { secret: "smoke-wrong-secret-at-least-32-bytes", expiresIn: "1h" })],
    ["wrong issuer", jwt.sign({ sub: claims.sub, username }, { issuer: "wrong-issuer", expiresIn: "1h" })],
    ["missing expiry", jwt.sign({ sub: claims.sub, username })],
  ];
  for (const [name, token] of invalidTokens) {
    await request(tradeUrl, path, 401, token ? bearer(token) : {});
    await request(authUrl, "/auth/me", 401, token ? bearer(token) : {});
    console.log(`PASS: ${name} token rejected by both services`);
  }
  const balance = await request(tradeUrl, path, 200, bearer(login.accessToken));
  assert.equal(balance.accountId, Number(accountId));
  for (const suffix of ["", "/positions", "/orders"]) {
    await request(tradeUrl, `/api/v1/accounts/${encodeURIComponent(accountId)}${suffix}`, 200, bearer(login.accessToken));
  }
  const refreshed = await request(authUrl, "/auth/refresh", 200, post({ refreshToken: login.refreshToken }));
  assert.notEqual(refreshed.accessToken, login.accessToken);
  tokens.verify(refreshed.accessToken);
  await request(tradeUrl, path, 200, bearer(refreshed.accessToken));
  await request(authUrl, "/auth/refresh", 401, post({ refreshToken: login.accessToken }));
  const second = await request(authUrl, "/auth/login", 200, post({ username, password }));
  await request(authUrl, "/auth/refresh", 401, post({ refreshToken: login.refreshToken }));
  await request(authUrl, "/auth/refresh", 200, post({ refreshToken: second.refreshToken }));
  const docs = await request(authUrl, "/docs-json", 200);
  for (const route of ["/auth/register", "/auth/login", "/auth/refresh"]) assert.ok(docs.paths[route]?.post);
  await request(tradeUrl, "/v3/api-docs", 200);
  console.log("PASS: registration, login, refresh, NestJS JWT -> Spring account routes, and OpenAPI");
}
main().catch(() => { console.error(`Authentication smoke check failed at ${step}; inspect service logs and configuration.`); process.exitCode = 1; });
