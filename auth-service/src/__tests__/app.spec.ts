import { Test } from "@nestjs/testing";
import { ConfigService } from "@nestjs/config";
import { AppModule } from "../app.module";
import { UserRepository } from "../repository/user.repository";

describe("Application configuration", () => {
  it.each([
    [undefined, "JWT_SECRET environment variable is required"],
    ["short-secret", "JWT_SECRET must be at least 32 bytes for HS256"],
  ])("fails startup for an absent or short JWT secret", async (secret, message) => {
    const config = { get: (key: string) => key === "JWT_SECRET" ? secret : undefined };
    await expect(Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(ConfigService).useValue(config)
      .overrideProvider(UserRepository).useValue({})
      .compile()).rejects.toThrow(message);
  });
});
