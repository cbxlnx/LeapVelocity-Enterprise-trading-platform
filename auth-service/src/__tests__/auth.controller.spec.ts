import { Test, TestingModule } from "@nestjs/testing";
import { INestApplication, ValidationPipe } from "@nestjs/common";
import supertest from "supertest";
import { AppModule } from "../app.module";
import { AuthService } from "../service/auth.service";
import { JwtService, type JwtSignOptions } from "@nestjs/jwt";
import { startTestDatabase, type TestDatabase } from "./support/test-database";
import { setupSwagger } from "../config/swagger.config";

const TEST_JWT_SECRET = "test-secret-key-32-bytes-minimum";

describe("AuthController (e2e)", () => {
  let app: INestApplication;
  let database: TestDatabase;
  const previousJwtSecret = process.env.JWT_SECRET;
  const previousDatabaseUrl = process.env.DATABASE_URL;

  beforeAll(async () => {
    database = await startTestDatabase();
    process.env.JWT_SECRET = TEST_JWT_SECRET;
    process.env.DATABASE_URL = database.url;

    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }));
    setupSwagger(app);
    await app.init();
    await app.get(AuthService).register("trader", "traderPass123");
  }, 30000);

  afterAll(async () => {
    await app?.close();
    await database?.close();
    if (previousJwtSecret === undefined) delete process.env.JWT_SECRET;
    else process.env.JWT_SECRET = previousJwtSecret;
    if (previousDatabaseUrl === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = previousDatabaseUrl;
  });

  describe("POST /auth/register", () => {
    it("should register a new user", () => {
      return supertest(app.getHttpServer())
        .post("/auth/register")
        .send({
          username: "newuser",
          password: "password123",
        })
        .expect(201)
        .expect((res) => {
          expect(res.body).toEqual({
            username: "newuser",
            registered: true,
          });
        });
    });

    it("should return 409 for duplicate username", () => {
      return supertest(app.getHttpServer())
        .post("/auth/register")
        .send({
          username: "dupuser2",
          password: "password123",
        })
        .expect(201)
        .then(() => {
          return supertest(app.getHttpServer())
            .post("/auth/register")
            .send({
              username: "dupuser2",
              password: "different123",
            })
            .expect(409);
        });
    });

    it("should not expose password in response", () => {
      return supertest(app.getHttpServer())
        .post("/auth/register")
        .send({
          username: "testuser123",
          password: "secretpassword123",
        })
        .expect(201)
        .expect((res) => {
          expect(JSON.stringify(res.body)).not.toContain("secretpassword123");
        });
    });
  });

  describe("POST /auth/login", () => {
    it("should login with correct credentials", () => {
      return supertest(app.getHttpServer())
        .post("/auth/register")
        .send({
          username: "loginuser123",
          password: "password123",
        })
        .expect(201)
        .then(() => {
          return supertest(app.getHttpServer())
            .post("/auth/login")
            .send({
              username: "loginuser123",
              password: "password123",
            })
            .expect(200)
            .expect((res) => {
              expect(res.body).toHaveProperty("accessToken");
              expect(res.body).toHaveProperty("refreshToken");
              expect(typeof res.body.accessToken).toBe("string");
              expect(typeof res.body.refreshToken).toBe("string");
            });
        });
    });

    it("should return 401 for wrong password", () => {
      return supertest(app.getHttpServer())
        .post("/auth/login")
        .send({
          username: "trader",
          password: "wrongpassword",
        })
        .expect(401);
    });

    it("should return 401 for non-existent user", () => {
      return supertest(app.getHttpServer())
        .post("/auth/login")
        .send({
          username: "nonexistentuser999",
          password: "password123",
        })
        .expect(401);
    });

    it("should not expose password in response", () => {
      return supertest(app.getHttpServer())
        .post("/auth/login")
        .send({
          username: "trader",
          password: "traderPass123",
        })
        .expect(200)
        .expect((res) => {
          expect(JSON.stringify(res.body)).not.toContain("traderPass123");
          expect(Object.keys(res.body).sort()).toEqual([
            "accessToken",
            "refreshToken",
          ]);
        });
    });

    it("should generate different tokens for each login", () => {
      let firstTokens : { accessToken: string; refreshToken: string };
      return supertest(app.getHttpServer())
        .post("/auth/login")
        .send({
          username: "trader",
          password: "traderPass123",
        })
        .expect(200)
        .then((res) => {
          firstTokens = res.body;
          return supertest(app.getHttpServer())
            .post("/auth/login")
            .send({
              username: "trader",
              password: "traderPass123",
            })
            .expect(200);
        })
        .then((res) => {
          expect(res.body.accessToken).not.toBe(firstTokens.accessToken);
          expect(res.body.refreshToken).not.toBe(firstTokens.refreshToken);
        });
    });
  });

  describe("POST /auth/refresh", () => {
    it("should issue a new access token for a valid refresh token", () => {
      return supertest(app.getHttpServer())
        .post("/auth/register")
        .send({
          username: "refreshuser123",
          password: "password123",
        })
        .expect(201)
        .then(() => {
          return supertest(app.getHttpServer())
            .post("/auth/login")
            .send({
              username: "refreshuser123",
              password: "password123",
            })
            .expect(200);
        })
        .then((loginResponse) => {
          return supertest(app.getHttpServer())
            .post("/auth/refresh")
            .send({
              refreshToken: loginResponse.body.refreshToken,
            })
            .expect(200)
            .expect((refreshResponse) => {
              expect(refreshResponse.body).toHaveProperty("accessToken");
              expect(typeof refreshResponse.body.accessToken).toBe("string");
              expect(refreshResponse.body.accessToken).not.toBe(loginResponse.body.accessToken);
            });
        });
    });

    it("should return 401 for an invalid refresh token", () => {
      return supertest(app.getHttpServer())
        .post("/auth/refresh")
        .send({
          refreshToken: "invalid-token",
        })
        .expect(401);
    });
  });

  describe("GET /auth/me", () => {
    let accessToken: string;
    let refreshToken: string;

    beforeAll(async () => {
      await app.get(AuthService).register("guarduser", "guardPassword123");
      const tokens = await app.get(AuthService).login("guarduser", "guardPassword123");
      accessToken = tokens.accessToken;
      refreshToken = tokens.refreshToken;
    });

    it("extracts the verified user with CurrentUser and exposes only the identity", () => {
      return supertest(app.getHttpServer())
        .get("/auth/me?username=anotheruser")
        .set("Authorization", `Bearer ${accessToken}`)
        .expect(200)
        .expect({ username: "guarduser" });
    });

    it("accepts a case-insensitive bearer scheme", () => {
      return supertest(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `bearer ${accessToken}`)
        .expect(200)
        .expect({ username: "guarduser" });
    });

    it.each([
      ["missing authorization", undefined],
      ["wrong authorization scheme", "Basic credentials"],
      ["missing bearer token", "Bearer"],
      ["multiple bearer tokens", "Bearer first second"],
      ["malformed JWT", "Bearer invalid-token"],
    ])("rejects %s", (_name, authorization) => {
      const request = supertest(app.getHttpServer()).get("/auth/me");
      if (authorization) request.set("Authorization", authorization);
      return request.expect(401);
    });

    it("rejects a real refresh token as authorization", () => {
      return supertest(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `Bearer ${refreshToken}`)
        .expect(401);
    });

    it.each<[string, Record<string, unknown>, JwtSignOptions]>([
      ["expired JWT", { sub: "guarduser" }, { expiresIn: -1 }],
      ["wrong signature", { sub: "guarduser" }, { secret: "another-test-secret-of-at-least-32-bytes", expiresIn: "1h" }],
      ["wrong issuer", { sub: "guarduser" }, { issuer: "another-issuer", expiresIn: "1h" }],
      ["wrong algorithm", { sub: "guarduser" }, { algorithm: "HS384", expiresIn: "1h" }],
      ["missing subject", {}, { expiresIn: "1h" }],
      ["empty subject", { sub: " " }, { expiresIn: "1h" }],
      ["non-string subject", { sub: 42 }, { expiresIn: "1h" }],
      ["missing expiration", { sub: "guarduser" }, {}],
      ["unexpected token type", { sub: "guarduser", type: "other" }, { expiresIn: "1h" }],
    ])("rejects a signed token with %s", (_name, payload, options) => {
      const token = app.get(JwtService).sign({ username: "guarduser", ...payload }, options);
      return supertest(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `Bearer ${token}`)
        .expect(401);
    });
  });

  describe("GET /health", () => {
    it("should return service health", () => {
      return supertest(app.getHttpServer())
        .get("/health")
        .expect(200)
        .expect((res) => {
          expect(res.body).toEqual({ status: "up" });
        });
    });
  });

  it("publishes the actual auth contracts in OpenAPI", async () => {
    await supertest(app.getHttpServer()).get("/docs-json").expect(200).expect((res) => {
      expect(res.body.paths["/auth/register"].post.responses["201"]).toBeDefined();
      expect(res.body.paths["/auth/login"].post.responses["200"]).toBeDefined();
      expect(res.body.paths["/auth/refresh"].post.security ?? []).toEqual([]);
      expect(res.body.paths["/auth/me"].get.security).toEqual([{ bearer: [] }]);
      expect(res.body.components.schemas.RefreshDto.required).toEqual(["refreshToken"]);
      expect(res.body.components.schemas.LoginResponseDto.required).toEqual(expect.arrayContaining(["accessToken", "refreshToken"]));
    });
    await supertest(app.getHttpServer()).get("/docs/").expect(200);
  });

  it.each([
    { username: "   ", password: "password123" },
    { username: "a".repeat(129), password: "password123" },
    { username: "oversized", password: "a".repeat(73) },
    { username: "oversized", password: "\u00e9".repeat(37) },
    { username: "injected", password: "password123", roles: ["ADMIN"] },
  ])("rejects unsafe registration DTOs", (body) => {
    return supertest(app.getHttpServer()).post("/auth/register").send(body).expect(400);
  });
});
