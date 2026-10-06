import { Test, TestingModule } from "@nestjs/testing";
import { INestApplication, ValidationPipe } from "@nestjs/common";
import supertest from "supertest";
import { AppModule } from "../app.module";

describe("AuthController (e2e)", () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(new ValidationPipe());
    await app.init();
  });

  afterAll(async () => {
    await app.close();
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
          username: "dave",
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
          username: "dave",
          password: "mission123",
        })
        .expect(200)
        .expect((res) => {
          expect(JSON.stringify(res.body)).not.toContain("mission123");
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
          username: "dave",
          password: "mission123",
        })
        .expect(200)
        .then((res) => {
          firstTokens = res.body;
          return supertest(app.getHttpServer())
            .post("/auth/login")
            .send({
              username: "dave",
              password: "mission123",
            })
            .expect(200);
        })
        .then((res) => {
          expect(res.body.accessToken).not.toBe(firstTokens.accessToken);
          expect(res.body.refreshToken).not.toBe(firstTokens.refreshToken);
        });
    });
  });
});
