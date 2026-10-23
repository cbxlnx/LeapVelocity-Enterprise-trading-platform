import { createParamDecorator, ExecutionContext, UnauthorizedException } from "@nestjs/common";
import type { AuthenticatedRequest, AuthenticatedUser } from "../types/authenticated-user";

export const CurrentUser = createParamDecorator(
  (_data: unknown, context: ExecutionContext): AuthenticatedUser => {
    const user = context.switchToHttp().getRequest<AuthenticatedRequest>().user;
    if (!user) {
      throw new UnauthorizedException("authentication required");
    }
    return user;
  },
);
