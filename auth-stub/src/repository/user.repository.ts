import { Injectable, OnModuleDestroy, OnModuleInit } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { Pool, type PoolConfig } from "pg";

export interface StoredUser {
  id: string;
  username: string;
  passwordHash: string;
  accountId: number | null;
}

interface StoredUserRow {
  id: string;
  username: string;
  passwordHash: string;
  accountId: string | null;
}

@Injectable()
export class UserRepository implements OnModuleInit, OnModuleDestroy {
  private readonly pool: Pool;

  constructor(configService: ConfigService) {
    const connectionString = configService.get<string>("DATABASE_URL");
    let poolConfig: PoolConfig;
    if (connectionString) {
      poolConfig = { connectionString };
    } else {
      const password = configService.get<string>("DB_PASSWORD");
      if (!password) {
        throw new Error("DATABASE_URL or DB_PASSWORD environment variable is required");
      }
      const port = Number(configService.get("DB_PORT") ?? configService.get("POSTGRES_PORT") ?? 5432);
      if (!Number.isInteger(port) || port < 1 || port > 65535) {
        throw new Error("Database port must be an integer between 1 and 65535");
      }
      poolConfig = {
        host: configService.get<string>("DB_HOST") ?? "localhost",
        port,
        database: configService.get<string>("DB_NAME") ?? "leapvelocity_trading_db",
        user: configService.get<string>("DB_USER") ?? "postgres",
        password,
      };
    }
    this.pool = new Pool({ ...poolConfig, connectionTimeoutMillis: 5000 });
    this.pool.on("error", () => {
      // Do not log connection strings or database error details.
      console.error("Authentication database connection error");
    });
  }

  async onModuleInit(): Promise<void> {
    try {
      await this.pool.query(
        "SELECT id, username, password_hash, account_id, refresh_token_hash, created_at, updated_at FROM public.users LIMIT 0",
      );
    } catch {
      await this.pool.end();
      throw new Error("Authentication database unavailable; check database configuration and apply db/tables/11_users.sql");
    }
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool.end();
  }

  async create(username: string, passwordHash: string): Promise<StoredUser | undefined> {
    const client = await this.pool.connect();

    try {
      await client.query("BEGIN");

      const accountIdResult = await client.query<{ id: string }>(
        `SELECT nextval(pg_get_serial_sequence('public.accounts', 'id'))::text AS id`,
      );
      const accountId = Number(accountIdResult.rows[0]?.id);

      if (!Number.isInteger(accountId) || accountId < 1) {
        throw new Error("Failed to reserve account identity");
      }

      const businessAccountId = `ACC-${String(accountId).padStart(3, "0")}`;

      await client.query(
        `INSERT INTO public.accounts (id, account_id, holder_name, cash_balance, status)
         VALUES ($1, $2, $3, $4, $5)`,
        [accountId, businessAccountId, username, "100000.00", "ACTIVE"],
      );

      const result = await client.query<StoredUserRow>(
        `INSERT INTO public.users (username, password_hash, account_id)
         VALUES ($1, $2, $3)
         RETURNING id::text AS id,
                   username,
                   password_hash AS "passwordHash",
                   account_id::text AS "accountId"`,
        [username, passwordHash, accountId],
      );

      await client.query("COMMIT");
      return this.mapStoredUser(result.rows[0]);
    } catch (error) {
      await client.query("ROLLBACK");
      if (this.isUniqueViolation(error)) {
        return undefined;
      }
      throw error;
    } finally {
      client.release();
    }
  }

  async findByUsername(username: string): Promise<StoredUser | undefined> {
    const result = await this.pool.query<StoredUserRow>(
      `SELECT id::text AS id,
              username,
              password_hash AS "passwordHash",
              account_id::text AS "accountId"
       FROM public.users WHERE username = $1`,
      [username],
    );
    return result.rows[0] ? this.mapStoredUser(result.rows[0]) : undefined;
  }

  async storeRefreshTokenHash(userId: string, tokenHash: string): Promise<void> {
    await this.pool.query(
      "UPDATE public.users SET refresh_token_hash = $2, updated_at = NOW() WHERE id = $1",
      [userId, tokenHash],
    );
  }

  async findByRefreshTokenHash(userId: string, tokenHash: string): Promise<StoredUser | undefined> {
    const result = await this.pool.query<StoredUserRow>(
      `SELECT id::text AS id,
              username,
              password_hash AS "passwordHash",
              account_id::text AS "accountId"
       FROM public.users WHERE id::text = $1 AND refresh_token_hash = $2`,
      [userId, tokenHash],
    );
    return result.rows[0] ? this.mapStoredUser(result.rows[0]) : undefined;
  }

  private mapStoredUser(row: StoredUserRow): StoredUser {
    return {
      id: row.id,
      username: row.username,
      passwordHash: row.passwordHash,
      accountId: row.accountId === null ? null : Number(row.accountId),
    };
  }

  private isUniqueViolation(error: unknown): boolean {
    return typeof error === "object" && error !== null && "code" in error && (error as { code?: string }).code === "23505";
  }
}
