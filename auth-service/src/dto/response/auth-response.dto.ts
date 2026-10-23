import { ApiProperty } from "@nestjs/swagger";

export class RegisterResponseDto {
  @ApiProperty() username!: string;
  @ApiProperty({ example: true }) registered!: boolean;
}

export class AccessTokenResponseDto {
  @ApiProperty() accessToken!: string;
}

export class LoginResponseDto extends AccessTokenResponseDto {
  @ApiProperty() refreshToken!: string;
}

export class CurrentUserResponseDto {
  @ApiProperty() username!: string;
}

export class AuthErrorResponseDto {
  @ApiProperty() statusCode!: number;
  @ApiProperty({ oneOf: [{ type: "string" }, { type: "array", items: { type: "string" } }] })
  message!: string | string[];
  @ApiProperty() error!: string;
}
