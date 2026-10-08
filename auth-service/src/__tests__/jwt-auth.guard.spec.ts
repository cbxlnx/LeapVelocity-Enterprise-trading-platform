import { ExecutionContext, UnauthorizedException } from "@nestjs/common";
import { JwtAuthGuard } from "../guard/jwt-auth.guard";
import { AuthService } from "../service/auth.service";

describe("JwtAuthGuard unit", () => {
  const auth = { validate: jest.fn() };
  const guard = new JwtAuthGuard(auth as unknown as AuthService);
  beforeEach(() => jest.resetAllMocks());
  function requestContext(authorization?: string) {
    const request = { headers: { authorization }, user: undefined as unknown };
    const context = { switchToHttp: () => ({ getRequest: () => request }) } as unknown as ExecutionContext;
    return { request, context };
  }

  it("attaches only the identity returned by verification", async () => {
    const { request, context } = requestContext("bearer signed-token");
    auth.validate.mockReturnValue({ username: "alice" });
    expect(await guard.canActivate(context)).toBe(true);
    expect(auth.validate).toHaveBeenCalledWith("signed-token");
    expect(request.user).toEqual({ username: "alice" });
  });

  it.each([undefined, "Basic credentials", "Bearer", "Bearer first second"])("rejects missing/malformed bearer credentials: %s", async (header) => {
    await expect(guard.canActivate(requestContext(header).context)).rejects.toThrow(UnauthorizedException);
    expect(auth.validate).not.toHaveBeenCalled();
  });

  it("propagates failed verification without attaching a user", async () => {
    const { request, context } = requestContext("Bearer bad-token");
    auth.validate.mockImplementation(() => { throw new UnauthorizedException(); });
    await expect(guard.canActivate(context)).rejects.toThrow(UnauthorizedException);
    expect(request.user).toBeUndefined();
  });
});
