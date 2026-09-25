package com.leapvelocity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing JWT access token")
public record AuthResponseDto(
        @Schema(description = "Type of token, e.g., Bearer", example = "Bearer")
        String tokenType,
        
        @Schema(description = "JWT access token for API authentication", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,
        
        @Schema(description = "Token expiration time in seconds", example = "3600")
        long expiresIn
) {
}
