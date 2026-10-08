import { ConfigService } from "@nestjs/config";
import { ThrottleService } from "../service/throttle.service";

describe("ThrottleService", () => {
  let service: ThrottleService;

  beforeEach(() => {
    const configService = { get: jest.fn() } as any;
    service = new ThrottleService(configService);
  });

  it("should not throttle unknown username", () => {
    expect(service.isThrottled("unknown")).toBe(false);
  });

  it("should not throttle after 1 failed attempt", () => {
    service.recordFailedAttempt("user1");
    expect(service.isThrottled("user1")).toBe(false);
  });

  it("should not throttle after 2 failed attempts", () => {
    service.recordFailedAttempt("user1");
    service.recordFailedAttempt("user1");
    expect(service.isThrottled("user1")).toBe(false);
  });

  it("should not throttle after 4 failed attempts", () => {
    for (let i = 0; i < 4; i++) {
      service.recordFailedAttempt("user1");
    }
    expect(service.isThrottled("user1")).toBe(false);
  });

  it("should throttle after max failed attempts (5)", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
    }
    expect(service.isThrottled("user1")).toBe(true);
  });

  it("should continue throttling after 6 attempts", () => {
    for (let i = 0; i < 6; i++) {
      service.recordFailedAttempt("user1");
    }
    expect(service.isThrottled("user1")).toBe(true);
  });

  it("should track different users independently", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
      service.recordFailedAttempt("user2");
    }
    expect(service.isThrottled("user1")).toBe(true);
    expect(service.isThrottled("user2")).toBe(true);
  });

  it("should track different users with different attempt counts", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
    }
    service.recordFailedAttempt("user2");
    service.recordFailedAttempt("user2");

    expect(service.isThrottled("user1")).toBe(true);
    expect(service.isThrottled("user2")).toBe(false);
  });

  it("should reset attempts on successful login", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
    }
    expect(service.isThrottled("user1")).toBe(true);
    service.resetAttempts("user1");
    expect(service.isThrottled("user1")).toBe(false);
  });

  it("should allow fresh attempts after reset", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
    }
    service.resetAttempts("user1");
    service.recordFailedAttempt("user1");
    expect(service.isThrottled("user1")).toBe(false);
  });

  it("should reset only specified user", () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailedAttempt("user1");
      service.recordFailedAttempt("user2");
    }
    service.resetAttempts("user1");
    expect(service.isThrottled("user1")).toBe(false);
    expect(service.isThrottled("user2")).toBe(true);
  });

  it("should return response delay", () => {
    expect(service.getResponseDelayMs()).toBe(100);
  });

  it("should handle reset on non-existent user", () => {
    expect(() => service.resetAttempts("nonexistent")).not.toThrow();
    expect(service.isThrottled("nonexistent")).toBe(false);
  });
});

