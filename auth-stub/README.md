# Authentication service

The Nest service exposes `POST /auth/register`, `POST /auth/login`,
`POST /auth/refresh`, the protected `GET /auth/me`, and the separate `GET /health`
endpoint.

## Protected routes

`GET /auth/me` uses `@UseGuards(JwtAuthGuard)` and `@CurrentUser()` to return
`{ "username": "..." }` from the verified access-token username claim. Send the access
JWT in `Authorization: Bearer <accessToken>`. The guard verifies the signature,
HS256 algorithm, configured issuer, expiration, subject, and username. Refresh tokens and
tokens without expiration cannot authorize this route. The decorator reads the
identity attached to the request by the guard, and does not return credentials
or tokens. Register, login, refresh, and health keep their existing public routes;
refresh independently validates its refresh token.

## Persistence

The existing schema uses numbered SQL files in `db/tables`, loaded by the
PostgreSQL container on first initialization.  `11_users.sql` adds authentication identities separately
from trading `accounts`, using the same database and initialization flow.

`users` contains an identity ID, unique username, bcrypt `password_hash`, nullable
`account_id` FK, timestamps, and one nullable SHA-256 `refresh_token_hash`.
Registration leaves `account_id` unset; it does not create or assign a trading
account. Deleting a linked account clears the link without deleting the user.
There are no seeded login credentials or database role fields. The existing
`TRADER` JWT claim remains for compatibility; this change adds no role model.

Fresh database volumes load the new SQL automatically. On an existing database,
apply `db/tables/11_users.sql` before starting auth; do not reset the volume.

## Configuration

Set a private `JWT_SECRET` of at least 32 bytes. Configure the existing
`DB_HOST`, `DB_PORT` (or `POSTGRES_PORT`), `DB_NAME`, `DB_USER`, and `DB_PASSWORD`
settings, or supply `DATABASE_URL`. Compose supplies the database settings and
waits for PostgreSQL health. The service fails startup if its database is
unavailable or the users table is missing. Keep `JWT_SECRET` and `JWT_ISSUER`
consistent with the Spring backend. Optional token lifetimes are `JWT_EXPIRES_IN`
(default `1h`) and `REFRESH_TOKEN_EXPIRES_IN` (default `7d`).

From `auth-stub`, run `npm ci`, `npm run build`, and `npm start` after configuration.
The legacy `server.js` entry point runs the same built Nest application.

## Refresh tokens

No session, logout, or token-rotation requirements. Refresh preserves the existing single-token-per-user behavior:
login replaces the stored hash, and refresh verifies HS256 signature, issuer,
expiration, token type, and the current hash before returning a new access JWT.
Refresh does not extend the refresh token's lifetime or issue a replacement.
Access JWTs and raw refresh tokens are never stored in the database.
Expired refresh tokens are rejected. Access JWT subjects now use the database
identity ID and carry a separate `username` claim. Log in again after upgrading;
old tokens without the username claim are rejected by both services.

## Verification

`npm run typecheck`, `npm test`, and `npm run build` check the service. Tests run
the production `pg` repository against isolated PGlite PostgreSQL socket servers,
including a temporary on-disk database for restart checks. These test databases
are independent of the configured development database and are removed afterward.

With both services running and a seeded trading account, `npm run test:smoke`
checks the HTTP flow through NestJS and Spring. Supply `JWT_SECRET` and optionally
`AUTH_URL`, `TRADE_URL`, `JWT_ISSUER`, and `SMOKE_ACCOUNT_ID`. The script registers
one identity, so use an expendable test database. It never prints passwords or JWTs.

## API documentation and service integration

Swagger UI is at `http://localhost:4000/docs`; OpenAPI is available at
`/docs-json` and `/docs-yaml`. `contracts/auth-api.yaml` is a snapshot generated
from `/docs-yaml`, reflecting the existing response shapes and Nest error format.

Spring Boot retains all `/api/v1/accounts/**` and `/api/v1/orders/**` business
routes. Its existing HS256 decoder and resource-server filter validate access
tokens locally using the same secret and issuer. `/api/**` requires a verified
access JWT; refresh tokens, missing expiry, invalid signatures, wrong issuers,
and expired tokens receive 401. Spring creates a JWT principal whose name is
the database user ID. Signature/expiry checks never use `decode()`.

Both services use zero expiry grace. Keep their clocks synchronized. Shared
HS256 means both services can sign tokens; this sprint preserves the existing
design rather than introducing keys or remote validation.

The existing fixed `TRADER` claim is preserved; there is no persisted role
model or permission hierarchy. Account ownership authorization, logout, refresh
rotation, and multiple concurrent refresh sessions remain outside this sprint.

See [the sprint report](../docs/authentication-sprint.md) for setup commands,
request examples, the initial assessment, changes, and verification evidence.
