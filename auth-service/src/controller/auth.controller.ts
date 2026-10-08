import { Body, Controller, Get, Post, HttpCode, UseGuards } from "@nestjs/common";
import { AuthService } from "../service/auth.service";
import { LoginDto } from "../dto/request/login.dto";
import { RefreshDto } from "../dto/request/refresh.dto";
import { RegisterDto } from "../dto/request/register.dto";
import { CurrentUser } from "../decorator/current-user.decorator";
import { JwtAuthGuard } from "../guard/jwt-auth.guard";
import type { AuthenticatedUser } from "../types/authenticated-user";
import { ApiBadRequestResponse, ApiBearerAuth, ApiConflictResponse, ApiCreatedResponse, ApiOkResponse, ApiTags, ApiUnauthorizedResponse } from "@nestjs/swagger";
import { AccessTokenResponseDto, AuthErrorResponseDto, CurrentUserResponseDto, LoginResponseDto, RegisterResponseDto } from "../dto/response/auth-response.dto";

@ApiTags("auth")
@Controller("auth")
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Get("me")
  @ApiBearerAuth()
  @ApiOkResponse({ type: CurrentUserResponseDto })
  @ApiUnauthorizedResponse({ type: AuthErrorResponseDto })
  @UseGuards(JwtAuthGuard)
  me(@CurrentUser() user: AuthenticatedUser): AuthenticatedUser {
    return user;
  }

  @Post("register")
  @ApiCreatedResponse({ type: RegisterResponseDto })
  @ApiBadRequestResponse({ type: AuthErrorResponseDto })
  @ApiConflictResponse({ type: AuthErrorResponseDto })
  register(@Body() body: RegisterDto) {
    return this.authService.register(body.username, body.password);
  }

  @Post("login")
  @HttpCode(200)
  @ApiOkResponse({ type: LoginResponseDto })
  @ApiBadRequestResponse({ type: AuthErrorResponseDto })
  @ApiUnauthorizedResponse({ type: AuthErrorResponseDto })
  login(@Body() body: LoginDto) {
    return this.authService.login(body.username, body.password);
  }

  @Post("refresh")
  @HttpCode(200)
  @ApiOkResponse({ type: AccessTokenResponseDto, description: "Requires an unexpired refreshToken in the request body." })
  @ApiBadRequestResponse({ type: AuthErrorResponseDto })
  @ApiUnauthorizedResponse({ type: AuthErrorResponseDto })
  refresh(@Body() body: RefreshDto) {
    return this.authService.refresh(body.refreshToken);
  }
}
