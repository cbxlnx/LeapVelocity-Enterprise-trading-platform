import { ConflictException, UnauthorizedException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import { AuthService } from "../service/auth.service";
import { UserRepository } from "../repository/user.repository";
import { TokenService } from "../service/token.service";
import { ThrottleService } from "../service/throttle.service";

describe("AuthService unit", () => {
  const users = { findByUsername: jest.fn(), create: jest.fn(), storeRefreshTokenHash: jest.fn(), findByRefreshTokenHash: jest.fn() };
  const tokens = { issue: jest.fn(), verify: jest.fn() };
  const throttle = { isThrottled: jest.fn(), recordFailedAttempt: jest.fn(), resetAttempts: jest.fn(), getResponseDelayMs: jest.fn(() => 0) };
  const auth = new AuthService(tokens as unknown as TokenService, users as unknown as UserRepository, throttle as unknown as ThrottleService);
  beforeEach(() => jest.resetAllMocks());

  it("stores a salted hash and returns only a safe registration response", async () => {
    users.create.mockResolvedValue(true);
    expect(await auth.register("alice", "password123")).toEqual({ username: "alice", registered: true });
    const hash = users.create.mock.calls[0][1];
    expect(hash).not.toBe("password123");
    expect(await bcrypt.compare("password123", hash)).toBe(true);
    expect(bcrypt.getRounds(hash)).toBe(12);
  });

  it("rejects existing users before hashing and handles insertion conflicts", async () => {
    users.findByUsername.mockResolvedValueOnce({ id: "42" });
    await expect(auth.register("alice", "password123")).rejects.toThrow(ConflictException);
    expect(users.create).not.toHaveBeenCalled();
    users.create.mockResolvedValue(false);
    await expect(auth.register("alice", "password123")).rejects.toThrow(ConflictException);
  });

  it("issues tokens only after successful password comparison and stores only the refresh hash", async () => {
    const user = { id: "42", username: "alice", passwordHash: await bcrypt.hash("password123", 4) };
    users.findByUsername.mockResolvedValue(user);
    tokens.issue.mockReturnValueOnce("access").mockReturnValueOnce("refresh");
    expect(await auth.login("alice", "password123")).toEqual({ accessToken: "access", refreshToken: "refresh" });
    expect(tokens.issue).toHaveBeenCalledWith(user);
    expect(tokens.issue).toHaveBeenCalledWith(user, "refresh");
    expect(users.storeRefreshTokenHash).toHaveBeenCalledWith("42", expect.stringMatching(/^[a-f0-9]{64}$/));
    await expect(auth.login("alice", "wrong")).rejects.toThrow(UnauthorizedException);
    users.findByUsername.mockResolvedValue(undefined);
    await expect(auth.login("missing", "password123")).rejects.toThrow(UnauthorizedException);
    expect(tokens.issue).toHaveBeenCalledTimes(2);
  });

  it("requires both verified refresh claims and a stored credential", async () => {
    tokens.verify.mockReturnValue({ sub: "42", username: "alice" });
    await expect(auth.refresh("refresh")).rejects.toThrow(UnauthorizedException);
    users.findByRefreshTokenHash.mockResolvedValue({ id: "42", username: "alice" });
    tokens.issue.mockReturnValue("new-access");
    expect(await auth.refresh("refresh")).toEqual({ accessToken: "new-access" });
    expect(tokens.verify).toHaveBeenCalledWith("refresh", "refresh");
  });

  describe("throttling", () => {
    it("should deny login when throttled", async () => {
      throttle.isThrottled.mockReturnValue(true);
      await expect(auth.login("alice", "password123")).rejects.toThrow(UnauthorizedException);
      expect(throttle.isThrottled).toHaveBeenCalledWith("alice");
    });

    it("should record failed attempt for non-existent user", async () => {
      throttle.isThrottled.mockReturnValue(false);
      users.findByUsername.mockResolvedValueOnce(undefined);
      await expect(auth.login("missing", "password123")).rejects.toThrow(UnauthorizedException);
      expect(throttle.recordFailedAttempt).toHaveBeenCalledWith("missing");
    });

    it("should record failed attempt for wrong password", async () => {
      throttle.isThrottled.mockReturnValue(false);
      const user = { id: "42", username: "alice", passwordHash: await bcrypt.hash("password123", 4) };
      users.findByUsername.mockResolvedValueOnce(user);
      await expect(auth.login("alice", "wrong")).rejects.toThrow(UnauthorizedException);
      expect(throttle.recordFailedAttempt).toHaveBeenCalledWith("alice");
    });

    it("should not record attempt for non-existent user and wrong password together", async () => {
      throttle.isThrottled.mockReturnValue(false);
      users.findByUsername.mockResolvedValue(undefined);
      try {
        await auth.login("missing", "anypassword");
      } catch {
        // Expected
      }
      expect(throttle.recordFailedAttempt).toHaveBeenCalledTimes(1);
    });

    it("should return same error message for non-existent user and wrong password", async () => {
      throttle.isThrottled.mockReturnValue(false);
      const user = { id: "42", username: "alice", passwordHash: await bcrypt.hash("password123", 4) };

      // Non-existent user
      users.findByUsername.mockResolvedValueOnce(undefined);
      let error1: any;
      try {
        await auth.login("missing", "password123");
      } catch (e) {
        error1 = e;
      }

      // Wrong password
      users.findByUsername.mockResolvedValueOnce(user);
      let error2: any;
      try {
        await auth.login("alice", "wrong");
      } catch (e) {
        error2 = e;
      }

      expect(error1.message).toBe("invalid username or password");
      expect(error2.message).toBe("invalid username or password");
      expect(throttle.recordFailedAttempt).toHaveBeenCalledTimes(2);
    });

    it("should reset throttle on successful login", async () => {
      throttle.isThrottled.mockReturnValue(false);
      const user = { id: "42", username: "alice", passwordHash: await bcrypt.hash("password123", 4) };
      users.findByUsername.mockResolvedValue(user);
      tokens.issue.mockReturnValueOnce("access").mockReturnValueOnce("refresh");

      await auth.login("alice", "password123");

      expect(throttle.resetAttempts).toHaveBeenCalledWith("alice");
    });

    it("should not reset throttle on failed login", async () => {
      throttle.isThrottled.mockReturnValue(false);
      users.findByUsername.mockResolvedValue(undefined);

      try {
        await auth.login("missing", "password123");
      } catch {
        // Expected
      }

      expect(throttle.resetAttempts).not.toHaveBeenCalled();
    });

    it("should apply response delay to success path", async () => {
      throttle.isThrottled.mockReturnValue(false);
      throttle.getResponseDelayMs.mockReturnValue(50);
      const user = { id: "42", username: "alice", passwordHash: await bcrypt.hash("password123", 4) };
      users.findByUsername.mockResolvedValue(user);
      tokens.issue.mockReturnValueOnce("access").mockReturnValueOnce("refresh");

      const startTime = Date.now();
      await auth.login("alice", "password123");
      const elapsed = Date.now() - startTime;

      expect(elapsed).toBeGreaterThanOrEqual(40);
    });

    it("should apply response delay to failure path", async () => {
      throttle.isThrottled.mockReturnValue(false);
      throttle.getResponseDelayMs.mockReturnValue(50);
      users.findByUsername.mockResolvedValue(undefined);

      const startTime = Date.now();
      try {
        await auth.login("missing", "password123");
      } catch {
        // Expected
      }
      const elapsed = Date.now() - startTime;

      expect(elapsed).toBeGreaterThanOrEqual(40);
    });

    it("should apply response delay to throttled path", async () => {
      throttle.isThrottled.mockReturnValue(true);
      throttle.getResponseDelayMs.mockReturnValue(50);

      const startTime = Date.now();
      try {
        await auth.login("alice", "password123");
      } catch {
        // Expected
      }
      const elapsed = Date.now() - startTime;

      expect(elapsed).toBeGreaterThanOrEqual(40);
    });
  });
});
