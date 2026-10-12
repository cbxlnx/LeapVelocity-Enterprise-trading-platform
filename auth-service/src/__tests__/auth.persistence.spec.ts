import { ConflictException, UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { JwtService } from "@nestjs/jwt";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import * as bcrypt from "bcrypt";
import { DEFAULT_JWT_ISSUER } from "../config/jwt.config";
import { UserRepository } from "../repository/user.repository";
import { AuthService } from "../service/auth.service";
import { TokenService } from "../service/token.service";
import { ThrottleService } from "../service/throttle.service";
import { startTestDatabase, type TestDatabase } from "./support/test-database";

const jwt = new JwtService({
  secret: "persistence-test-secret-at-least-32-bytes",
  signOptions: { algorithm: "HS256", issuer: DEFAULT_JWT_ISSUER },
});

describe("Authentication persistence", () => {
  let dataDir: string;
  let database: TestDatabase;
  let repository: UserRepository;
  let service: AuthService;

  async function connect(): Promise<void> {
    const config = new ConfigService({ DATABASE_URL: database.url });
    repository = new UserRepository(config);
    await repository.onModuleInit();
    service = new AuthService(new TokenService(jwt, config), repository, new ThrottleService(config));
  }

  beforeAll(async () => {
    dataDir = await mkdtemp(join(tmpdir(), "leapvelocity-auth-test-"));
    database = await startTestDatabase(dataDir);
    await connect();
  }, 30000);

  afterAll(async () => {
    await repository?.onModuleDestroy();
    await database?.close();
    if (dataDir) {
      if (dirname(resolve(dataDir)) !== resolve(tmpdir()) || !basename(dataDir).startsWith("leapvelocity-auth-test-")) {
        throw new Error("Refusing to remove a directory outside the isolated test database");
      }
      await rm(dataDir, { recursive: true, force: true });
    }
  });

  it("persists credentials and the refresh hash across database and service restarts", async () => {
    const password = "persistentPassword123";
    await service.register("persistentuser", password);
    const tokens = await service.login("persistentuser", password);
    const stored = await database.db.query<{
      password_hash: string;
      refresh_token_hash: string;
      account_id: string | null;
      created_at: Date;
      updated_at: Date;
    }>("SELECT * FROM users WHERE username = $1", ["persistentuser"]);
    const user = stored.rows[0];
    expect(await bcrypt.compare(password, user.password_hash)).toBe(true);
    expect(user.password_hash).not.toBe(password);
    expect(user.refresh_token_hash).toBe(createHash("sha256").update(tokens.refreshToken).digest("hex"));
    expect(user.account_id).not.toBeNull();
    expect(user.created_at).toBeDefined();
    expect(user.updated_at).toBeDefined();
    expect(JSON.stringify(stored.rows)).not.toContain(tokens.accessToken);
    expect(JSON.stringify(stored.rows)).not.toContain(tokens.refreshToken);

    const account = await database.db.query<{
      account_id: string;
      holder_name: string;
      cash_balance: string;
      status: string;
    }>("SELECT account_id, holder_name, cash_balance::text AS cash_balance, status FROM accounts WHERE id = $1", [user.account_id]);
    expect(account.rows).toHaveLength(1);
    expect(account.rows[0]).toMatchObject({
      account_id: expect.stringMatching(/^ACC-\d{3,}$/),
      holder_name: "persistentuser",
      cash_balance: "100000.00",
      status: "ACTIVE",
    });

    await repository.onModuleDestroy();
    await database.close();
    database = await startTestDatabase(dataDir, false);
    await connect();

    await expect(service.refresh(tokens.refreshToken)).resolves.toHaveProperty("accessToken");
    await expect(service.login("persistentuser", password)).resolves.toHaveProperty("accessToken");
  }, 30000);

  it("returns one conflict for simultaneous registrations of the same username", async () => {
    const results = await Promise.allSettled([
      service.register("sameusername", "password123"),
      service.register("sameusername", "password456"),
    ]);
    expect(results.filter((result) => result.status === "fulfilled")).toHaveLength(1);
    const rejected = results.find((result) => result.status === "rejected");
    expect(rejected?.reason).toBeInstanceOf(ConflictException);
    const stored = await database.db.query<{ count: string }>("SELECT COUNT(*)::text AS count FROM users WHERE username = $1", ["sameusername"]);
    const accounts = await database.db.query<{ count: string }>("SELECT COUNT(*)::text AS count FROM accounts WHERE holder_name = $1", ["sameusername"]);
    expect(stored.rows[0].count).toBe("1");
    expect(accounts.rows[0].count).toBe("1");
  });

  it("invalidates the previous refresh token when the user logs in again", async () => {
    await service.register("latestlogin", "password123");
    const first = await service.login("latestlogin", "password123");
    const second = await service.login("latestlogin", "password123");
    await expect(service.refresh(first.refreshToken)).rejects.toThrow(UnauthorizedException);
    await expect(service.refresh(second.refreshToken)).resolves.toHaveProperty("accessToken");
  });

  it.each([
    ["expired", { type: "refresh" }, { expiresIn: -1 }],
    ["wrong issuer", { type: "refresh" }, { issuer: "other-issuer", expiresIn: "1h" }],
    ["wrong algorithm", { type: "refresh" }, { algorithm: "HS384", expiresIn: "1h" }],
    ["access token", {}, { expiresIn: "1h" }],
  ] as const)("rejects a stored %s token", async (name, payload, options) => {
    const username = `invalid-${name}`;
    await service.register(username, "password123");
    const user = await repository.findByUsername(username);
    const token = jwt.sign({ sub: user!.id, username, ...payload }, options);
    await repository.storeRefreshTokenHash(user!.id, createHash("sha256").update(token).digest("hex"));
    await expect(service.refresh(token)).rejects.toThrow(UnauthorizedException);
  });

  it("keeps authentication identities when a linked trading account is deleted", async () => {
    await service.register("linkeduser", "password123");
    await database.db.exec(
      "INSERT INTO accounts (account_id, holder_name, cash_balance, status) VALUES ('ACC-TEST', 'Test Holder', 0, 'ACTIVE')",
    );
    await database.db.exec(
      "UPDATE users SET account_id = (SELECT id FROM accounts WHERE account_id = 'ACC-TEST') WHERE username = 'linkeduser'",
    );
    await expect(database.db.exec("UPDATE users SET account_id = -1 WHERE username = 'linkeduser'")).rejects.toMatchObject({ code: "23503" });
    await database.db.exec("DELETE FROM accounts WHERE account_id = 'ACC-TEST'");
    const stored = await database.db.query<{ account_id: string | null }>(
      "SELECT account_id FROM users WHERE username = 'linkeduser'",
    );
    expect(stored.rows).toEqual([{ account_id: null }]);
    await expect(service.login("linkeduser", "password123")).resolves.toHaveProperty("accessToken");
    const accountColumns = await database.db.query<{ column_name: string }>(
      "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts'",
    );
    expect(accountColumns.rows.map((column) => column.column_name)).not.toContain("password_hash");
  });

  it("can apply the additive users SQL again without deleting identities", async () => {
    await database.db.exec(await readFile(resolve(__dirname, "../../../db/tables/11_users.sql"), "utf8"));
    expect(await repository.findByUsername("persistentuser")).toBeDefined();
  });
});
