import { IsString, IsNotEmpty, MaxLength, Matches, IsByteLength } from "class-validator";
import { ApiProperty } from "@nestjs/swagger";

export class LoginDto {
  @ApiProperty({ maxLength: 128 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  @Matches(/\S/)
  username!: string;

  @ApiProperty({ description: "Maximum 72 UTF-8 bytes (bcrypt limit).", format: "password", writeOnly: true })
  @IsString()
  @IsNotEmpty()
  @IsByteLength(0, 72)
  password!: string;
}
