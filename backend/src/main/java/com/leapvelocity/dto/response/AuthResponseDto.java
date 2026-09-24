package com.leapvelocity.dto.response;

public record AuthResponseDto(
        String tokenType,
        String accessToken,
        long expiresIn
) {
}
