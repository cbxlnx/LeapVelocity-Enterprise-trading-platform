import { IsNotEmpty, IsString } from "class-validator";
import { ApiProperty } from "@nestjs/swagger";

export class RefreshDto {
  @ApiProperty({ description: "Unexpired refresh JWT returned by login." })
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}
