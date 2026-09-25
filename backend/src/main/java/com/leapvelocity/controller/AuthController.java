package com.leapvelocity.controller;

import com.leapvelocity.dto.request.LoginRequestDto;
import com.leapvelocity.dto.response.AuthResponseDto;
import com.leapvelocity.service.JwtTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Authentication endpoints for user login and JWT token management")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;

    public AuthController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Operation(summary = "User login", description = "Authenticate user with username and password, returns JWT access token for subsequent API calls")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT token issued"),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing username or password)"),
            @ApiResponse(responseCode = "401", description = "Authentication failed (invalid credentials)")
    })
    @PostMapping("/login")
    public AuthResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return jwtTokenService.issueToken(request.username());
    }
}
