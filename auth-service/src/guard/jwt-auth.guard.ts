import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from "@nestjs/common";
import { AuthService } from "../service/auth.service";
import type { AuthenticatedRequest } from "../types/authenticated-user";

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly auth: AuthService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const authorization = request.headers.authorization;
    const match = typeof authorization === "string" ? /^Bearer[ \t]+(\S+)$/i.exec(authorization.trim()) : null;

    if (!match) {
      throw new UnauthorizedException("a bearer access token is required");
    }

    request.user = this.auth.validate(match[1]);
    return true;
  }
}
