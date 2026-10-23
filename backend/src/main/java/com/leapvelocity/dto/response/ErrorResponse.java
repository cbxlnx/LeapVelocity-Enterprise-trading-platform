package com.leapvelocity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leapvelocity.entities.enums.api.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Standard error response DTO for API responses.
 * Includes error code, message, timestamp, and optional details for debugging.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private Object details;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
    }

    public ErrorResponse(String code, String message) {
        this();
        this.code = code;
        this.message = message;
    }

    public ErrorResponse(String code, String message, String path) {
        this(code, message);
        this.path = path;
    }

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public ErrorResponse(ErrorCode errorCode, String path) {
        this(errorCode.getCode(), errorCode.getMessage(), path);
    }

    public ErrorResponse(ErrorCode errorCode, Object details) {
        this(errorCode.getCode(), errorCode.getMessage());
        this.details = details;
    }

    public ErrorResponse(ErrorCode errorCode, String path, Object details) {
        this(errorCode.getCode(), errorCode.getMessage(), path);
        this.details = details;
    }

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getDetails() {
        return details;
    }

    public void setDetails(Object details) {
        this.details = details;
    }
}
