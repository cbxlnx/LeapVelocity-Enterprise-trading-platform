package com.leapvelocity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login request containing user credentials")
public record LoginRequestDto(
        @Schema(description = "Username for authentication", example = "john_trader")
        @NotBlank String username,
        
        @Schema(description = "Password for authentication", example = "secure_password_123")
        @NotBlank String password
) {
}
