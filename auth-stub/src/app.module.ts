import { Module } from "@nestjs/common";
import { ConfigModule, ConfigService } from "@nestjs/config";
import { JwtModule } from "@nestjs/jwt";
import { AuthController } from "./controller/auth.controller";
import { DEFAULT_JWT_ISSUER, DEFAULT_JWT_SECRET } from "./config/jwt.config";
import { AuthService } from "./service/auth.service";

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        secret: configService.get<string>("JWT_SECRET") ?? DEFAULT_JWT_SECRET,
        signOptions: {
          algorithm: "HS256",
          issuer: configService.get<string>("JWT_ISSUER") ?? DEFAULT_JWT_ISSUER,
        },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService],
})
export class AppModule {}
