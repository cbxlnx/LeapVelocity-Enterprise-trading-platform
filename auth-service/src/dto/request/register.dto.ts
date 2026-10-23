import { IsString, IsNotEmpty, MinLength, MaxLength, Matches, IsByteLength } from "class-validator";
import { ApiProperty } from "@nestjs/swagger";

export class RegisterDto {
  @ApiProperty({ minLength: 3, maxLength: 128 })
  @IsString()
  @IsNotEmpty()
  @MinLength(3)
  @MaxLength(128)
  @Matches(/\S/)
  username!: string;

  @ApiProperty({ minLength: 8, description: "Maximum 72 UTF-8 bytes (bcrypt limit).", format: "password", writeOnly: true })
  @IsString()
  @MinLength(8)
  @IsByteLength(0, 72)
  password!: string;
}
