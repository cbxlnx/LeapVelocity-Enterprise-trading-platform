import { PGlite } from "@electric-sql/pglite";
import { PGLiteSocketServer } from "@electric-sql/pglite-socket";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

export async function startTestDatabase(dataDir?: string, initialize = true) {
  const db = await PGlite.create(dataDir);
  try {
    if (initialize) {
      for (const name of ["01_accounts.sql", "11_users.sql"]) {
        await db.exec(await readFile(resolve(__dirname, "../../../../db/tables", name), "utf8"));
      }
    }
    const server = new PGLiteSocketServer({ db, host: "127.0.0.1", port: 0, maxConnections: 10 });
    await server.start();
    return {
      db,
      url: `postgresql://postgres@${server.getServerConn()}/postgres`,
      async close() {
        await server.stop();
        await db.close();
      },
    };
  } catch (error) {
    await db.close();
    throw error;
  }
}

export type TestDatabase = Awaited<ReturnType<typeof startTestDatabase>>;
