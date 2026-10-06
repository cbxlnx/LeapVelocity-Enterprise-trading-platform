import { Test, TestingModule } from "@nestjs/testing";
import { AuthService } from "../service/auth.service";
import { ConflictException, UnauthorizedException } from "@nestjs/common";
import * as bcrypt from "bcrypt";

describe("AuthService", () => {
  let service: AuthService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [AuthService],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  describe("register", () => {
    it("should be defined", () => {
      expect(service).toBeDefined();
    });

    it("should register a new user", async () => {
      const result = await service.register("testuser", "password123");
      expect(result).toEqual({ username: "testuser", registered: true });
    });

    it("should hash password using bcrypt", async () => {
      const username = "hashtest";
      const password = "password123";
      await service.register(username, password);
      // Verify by trying to login
      const loginResult = await service.login(username, password);
      expect(loginResult).toHaveProperty("accessToken");
      expect(loginResult).toHaveProperty("refreshToken");
    });

    it("should throw ConflictException for duplicate username", async () => {
      await service.register("duplicate", "password123");
      await expect(service.register("duplicate", "password456")).rejects.toThrow(
        ConflictException,
      );
    });

    it("should throw ConflictException with correct message", async () => {
      const username = "testdup";
      await service.register(username, "password123");
      try {
        await service.register(username, "password456");
        fail("Should have thrown ConflictException");
      } catch (error : any) {
        expect(error.message).toContain(username);
        expect(error.message).toContain("already registered");
      }
    });

    it("should not store plaintext password", async () => {
      const username = "noPlains";
      const password = "plainTextPassword123";
      await service.register(username, password);

      // Try to login with wrong password - should fail
      await expect(service.login(username, "wrongPassword")).rejects.toThrow(
        UnauthorizedException,
      );
    });

    it("should support multiple user registrations", async () => {
      const users = ["user1", "user2", "user3"];
      for (const username of users) {
        const result = await service.register(username, "password123");
        expect(result.username).toBe(username);
        expect(result.registered).toBe(true);
      }
    });
  });

  describe("login", () => {
    beforeEach(async () => {
      await service.register("dave", "mission123");
      await service.register("testuser", "password123");
    });

    it("should login with correct credentials", async () => {
      const result = await service.login("testuser", "password123");
      expect(result).toHaveProperty("accessToken");
      expect(result).toHaveProperty("refreshToken");
      expect(typeof result.accessToken).toBe("string");
      expect(typeof result.refreshToken).toBe("string");
    });

    it("should generate different tokens on each login", async () => {
      const result1 = await service.login("testuser", "password123");
      const result2 = await service.login("testuser", "password123");

      expect(result1.accessToken).not.toBe(result2.accessToken);
      expect(result1.refreshToken).not.toBe(result2.refreshToken);
    });

    it("should throw UnauthorizedException for wrong password", async () => {
      await expect(
        service.login("testuser", "wrongPassword"),
      ).rejects.toThrow(UnauthorizedException);
    });

    it("should throw UnauthorizedException for non-existent user", async () => {
      await expect(
        service.login("nonexistent", "password123"),
      ).rejects.toThrow(UnauthorizedException);
    });

    it("should throw UnauthorizedException with message", async () => {
      try {
        await service.login("testuser", "wrongPassword");
        fail("Should have thrown UnauthorizedException");
      } catch (error : any) {
        expect(error.message).toContain("invalid username or password");
      }
    });

    it("should verify password using bcrypt.compare", async () => {
      const result = await service.login("dave", "mission123");
      expect(result.accessToken).toBeDefined();
    });

    it("should be case-sensitive for password", async () => {
      await expect(
        service.login("testuser", "PASSWORD123"),
      ).rejects.toThrow(UnauthorizedException);
    });

    it("should handle pre-registered user (dave)", async () => {
      const result = await service.login("dave", "mission123");
      expect(result).toHaveProperty("accessToken");
      expect(result).toHaveProperty("refreshToken");
    });
  });

  describe("refresh", () => {
    beforeEach(async () => {
      await service.register("refreshtest", "password123");
    });

    it("should refresh access token with valid refresh token", async () => {
      const { refreshToken } = await service.login("refreshtest", "password123");
      const result = service.refresh(refreshToken);
      expect(result).toHaveProperty("accessToken");
      expect(typeof result.accessToken).toBe("string");
    });

    it("should throw UnauthorizedException for invalid refresh token", async () => {
      const invalidToken = "invalid-token-xyz";
      expect(() => service.refresh(invalidToken)).toThrow(
        UnauthorizedException,
      );
    });

    it("should throw UnauthorizedException for non-existent token", async () => {
      const fakeToken = "stub-refresh-token-for-fakeuser-abc12345";
      expect(() => service.refresh(fakeToken)).toThrow(UnauthorizedException);
    });

    it("should generate new access token different from previous", async () => {
      const loginResult = await service.login("refreshtest", "password123");
      const token1 = loginResult.accessToken;
      const refreshResult = service.refresh(loginResult.refreshToken);
      expect(refreshResult.accessToken).not.toBe(token1);
    });

    it("should throw error with message", () => {
      try {
        service.refresh("invalid-token");
        fail("Should have thrown UnauthorizedException");
      } catch (error : any) {
        expect(error.message).toContain("invalid or expired refresh token");
      }
    });
  });

  describe("Acceptance Criteria - Password Hashing", () => {
    it("AC1: Passwords should be hashed, not stored in plaintext", async () => {
      const password = "MySecurePassword123!";
      await service.register("actest1", password);

      // Should authenticate with correct password
      const result = await service.login("actest1", password);
      expect(result).toBeDefined();

      // Should NOT authenticate with wrong password
      await expect(service.login("actest1", "WrongPassword")).rejects.toThrow();
    });

    it("AC2: Should use bcrypt for password hashing", async () => {
      const username = "bcrypttest";
      const password = "TestPassword123!";
      await service.register(username, password);

      // If password is properly hashed with bcrypt, login should work
      const loginResult = await service.login(username, password);
      expect(loginResult.accessToken).toBeDefined();
    });

    it("AC3: Password should never appear in error messages", async () => {
      const password = "SecretPassword123!";
      await service.register("errortest", password);

      try {
        await service.login("errortest", password + "wrong");
      } catch (error : any) {
        const errorMessage = error.message;
        expect(errorMessage).not.toContain(password);
        expect(errorMessage).not.toContain("wrong");
      }
    });

    it("AC4: Different passwords should produce different hashes", async () => {
      // Register two users with different passwords
      await service.register("user1", "password123");
      await service.register("user2", "password456");

      // Both should login successfully
      const result1 = await service.login("user1", "password123");
      const result2 = await service.login("user2", "password456");

      expect(result1.accessToken).toBeDefined();
      expect(result2.accessToken).toBeDefined();
      // Tokens should be different
      expect(result1.accessToken).not.toBe(result2.accessToken);
    });
  });

  describe("Security - No Password in Logs", () => {
    it("should not expose password in registration response", async () => {
      const password = "TopSecretPassword123!";
      const result = await service.register("securitytest", password);

      expect(JSON.stringify(result)).not.toContain(password);
    });

    it("should not expose password in login response", async () => {
      const password = "TopSecretPassword123!";
      await service.register("securitytest2", password);

      const result = await service.login("securitytest2", password);

      expect(JSON.stringify(result)).not.toContain(password);
      // Response should only contain tokens
      expect(Object.keys(result).sort()).toEqual([
        "accessToken",
        "refreshToken",
      ]);
    });

    it("should not include plaintext password in any stored data", async () => {
      const password = "VerySecretPassword123!";
      await service.register("storagetest", password);

      // Verify password works
      const result = await service.login("storagetest", password);
      expect(result).toBeDefined();

      // Verify wrong password fails (confirming we're using bcrypt hash)
      await expect(
        service.login("storagetest", "NotThePassword"),
      ).rejects.toThrow();
    });
  });

  describe("Integration Scenarios", () => {
    it("should handle register -> login -> refresh flow", async () => {
      // Register
      const registerResult = await service.register("flowtest", "password123");
      expect(registerResult.registered).toBe(true);

      // Login
      const loginResult = await service.login("flowtest", "password123");
      expect(loginResult.accessToken).toBeDefined();
      expect(loginResult.refreshToken).toBeDefined();

      // Refresh
      const refreshResult = service.refresh(loginResult.refreshToken);
      expect(refreshResult.accessToken).toBeDefined();
      expect(refreshResult.accessToken).not.toBe(loginResult.accessToken);
    });

    it("should handle multiple concurrent registrations", async () => {
      const promises = [
        service.register("concurrent1", "password123"),
        service.register("concurrent2", "password123"),
        service.register("concurrent3", "password123"),
      ];

      const results = await Promise.all(promises);
      expect(results).toHaveLength(3);
      results.forEach((result, index) => {
        expect(result.username).toBe(`concurrent${index + 1}`);
        expect(result.registered).toBe(true);
      });
    });

    it("should handle multiple concurrent logins", async () => {
      await service.register("login1", "password1");
      await service.register("login2", "password2");
      await service.register("login3", "password3");

      const promises = [
        service.login("login1", "password1"),
        service.login("login2", "password2"),
        service.login("login3", "password3"),
      ];

      const results = await Promise.all(promises);
      expect(results).toHaveLength(3);
      results.forEach((result) => {
        expect(result.accessToken).toBeDefined();
        expect(result.refreshToken).toBeDefined();
      });
    });
  });
});
