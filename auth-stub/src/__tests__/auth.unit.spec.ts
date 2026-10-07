import { ConflictException, UnauthorizedException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import { AuthService } from "../service/auth.service";
import { UserRepository } from "../repository/user.repository";
import { TokenService } from "../service/token.service";

describe("AuthService unit", () => {
  const users = { findByUsername: jest.fn(), create: jest.fn(), storeRefreshTokenHash: jest.fn(), findByRefreshTokenHash: jest.fn() };
  const tokens = { issue: jest.fn(), verify: jest.fn() };
  const auth = new AuthService(tokens as unknown as TokenService, users as unknown as UserRepository);
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
});
