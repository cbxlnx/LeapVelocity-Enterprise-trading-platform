package com.leapvelocity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leapvelocity.entities.enums.api.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Error response DTO for validation errors with multiple field-level errors.
 * Used for HTTP 400 Bad Request responses with detailed validation failure information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {
    private String code;
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> errors;

    public ValidationErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.errors = new ArrayList<>();
    }

    public ValidationErrorResponse(String path) {
        this();
        this.path = path;
        this.code = ErrorCode.VALIDATION_FAILED.getCode();
        this.message = ErrorCode.VALIDATION_FAILED.getMessage();
        this.status = ErrorCode.VALIDATION_FAILED.getHttpStatusCode();
    }

    public void addFieldError(String field, String message) {
        this.errors.add(new FieldError(field, message));
    }

    public void addFieldError(String field, String message, Object rejectedValue) {
        this.errors.add(new FieldError(field, message, rejectedValue));
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public List<FieldError> getErrors() {
        return errors;
    }

    public void setErrors(List<FieldError> errors) {
        this.errors = errors;
    }

    /**
     * Inner class representing a field-level validation error.
     */
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getRejectedValue() {
            return rejectedValue;
        }

        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }
    }
}
