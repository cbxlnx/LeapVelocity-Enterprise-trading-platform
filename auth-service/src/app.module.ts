import { Module } from "@nestjs/common";
import { ConfigModule, ConfigService } from "@nestjs/config";
import { JwtModule } from "@nestjs/jwt";
import { AuthController } from "./controller/auth.controller";
import { HealthController } from "./controller/health.controller";
import { DEFAULT_JWT_ISSUER } from "./config/jwt.config";
import { AuthService } from "./service/auth.service";
import { UserRepository } from "./repository/user.repository";
import { JwtAuthGuard } from "./guard/jwt-auth.guard";
import { TokenService } from "./service/token.service";
import { ThrottleService } from "./service/throttle.service";

function requiredJwtSecret(configService: ConfigService): string {
  const secret = configService.get<string>("JWT_SECRET");

  if (!secret) {
    throw new Error("JWT_SECRET environment variable is required");
  }

  if (Buffer.byteLength(secret, "utf8") < 32) {
    throw new Error("JWT_SECRET must be at least 32 bytes for HS256");
  }

  return secret;
}

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        secret: requiredJwtSecret(configService),
        signOptions: {
          algorithm: "HS256",
          issuer: configService.get<string>("JWT_ISSUER") ?? DEFAULT_JWT_ISSUER,
        },
      }),
    }),
  ],
  controllers: [AuthController, HealthController],
  providers: [AuthService, TokenService, UserRepository, JwtAuthGuard, ThrottleService],
})
export class AppModule {}
