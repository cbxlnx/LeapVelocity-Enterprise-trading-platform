package com.leapvelocity.dto.response;

import com.leapvelocity.entities.enums.api.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Error response DTOs")
class ErrorResponseTest {

    @Test
    @DisplayName("default error response initializes timestamp")
    void defaultErrorResponseInitializesTimestamp() {
        ErrorResponse response = new ErrorResponse();

        assertNotNull(response.getTimestamp());
        assertNull(response.getCode());
        assertNull(response.getMessage());
    }

    @Test
    @DisplayName("error response constructors populate code message path and details")
    void errorResponseConstructorsPopulateFields() {
        ErrorResponse direct = new ErrorResponse("E-1", "Something broke", "/orders");
        ErrorResponse fromCode = new ErrorResponse(ErrorCode.ACCOUNT_NOT_FOUND);
        ErrorResponse withPath = new ErrorResponse(ErrorCode.ACCOUNT_NOT_ACTIVE, "/accounts/1");
        ErrorResponse withDetails = new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, List.of("stack", "id"));
        ErrorResponse full = new ErrorResponse(ErrorCode.VALIDATION_FAILED, "/orders", List.of("field"));

        assertEquals("E-1", direct.getCode());
        assertEquals("Something broke", direct.getMessage());
        assertEquals("/orders", direct.getPath());

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), fromCode.getCode());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getMessage(), fromCode.getMessage());

        assertEquals("/accounts/1", withPath.getPath());
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE.getCode(), withPath.getCode());

        assertEquals(List.of("stack", "id"), withDetails.getDetails());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), withDetails.getMessage());

        assertEquals("/orders", full.getPath());
        assertEquals(List.of("field"), full.getDetails());
    }

    @Test
    @DisplayName("error response setters update mutable fields")
    void errorResponseSettersUpdateMutableFields() {
        ErrorResponse response = new ErrorResponse();
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 9, 14, 30);

        response.setCode("AUTH-401");
        response.setMessage("Invalid token");
        response.setPath("/api/v1/accounts/1");
        response.setDetails("trace-123");
        response.setTimestamp(timestamp);

        assertEquals("AUTH-401", response.getCode());
        assertEquals("Invalid token", response.getMessage());
        assertEquals("/api/v1/accounts/1", response.getPath());
        assertEquals("trace-123", response.getDetails());
        assertEquals(timestamp, response.getTimestamp());
    }

    @Test
    @DisplayName("validation error response tracks field level failures")
    void validationErrorResponseTracksFieldLevelFailures() {
        ValidationErrorResponse response = new ValidationErrorResponse("/api/v1/orders");

        response.addFieldError("symbol", "must not be blank");
        response.addFieldError("quantity", "must be positive", -1);

        assertEquals(ErrorCode.VALIDATION_FAILED.getCode(), response.getCode());
        assertEquals(ErrorCode.VALIDATION_FAILED.getMessage(), response.getMessage());
        assertEquals("/api/v1/orders", response.getPath());
        assertNotNull(response.getTimestamp());
        assertEquals(2, response.getErrors().size());
        assertEquals("symbol", response.getErrors().get(0).getField());
        assertEquals("must not be blank", response.getErrors().get(0).getMessage());
        assertNull(response.getErrors().get(0).getRejectedValue());
        assertEquals(-1, response.getErrors().get(1).getRejectedValue());
    }

    @Test
    @DisplayName("validation error response setters replace mutable state")
    void validationErrorResponseSettersReplaceMutableState() {
        ValidationErrorResponse response = new ValidationErrorResponse();
        ValidationErrorResponse.FieldError fieldError = new ValidationErrorResponse.FieldError("price", "invalid", 0);
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 9, 15, 0);

        response.setCode("VAL-422");
        response.setMessage("Validation failed");
        response.setPath("/api/v1/orders");
        response.setTimestamp(timestamp);
        response.setErrors(List.of(fieldError));

        assertEquals("VAL-422", response.getCode());
        assertEquals("Validation failed", response.getMessage());
        assertEquals("/api/v1/orders", response.getPath());
        assertEquals(timestamp, response.getTimestamp());
        assertEquals(1, response.getErrors().size());
        assertEquals("price", response.getErrors().get(0).getField());
        assertEquals("invalid", response.getErrors().get(0).getMessage());
        assertEquals(0, response.getErrors().get(0).getRejectedValue());
    }

    @Test
    @DisplayName("field error setters update nested validation details")
    void fieldErrorSettersUpdateNestedValidationDetails() {
        ValidationErrorResponse.FieldError fieldError = new ValidationErrorResponse.FieldError("quantity", "bad value");

        fieldError.setField("averageCost");
        fieldError.setMessage("must be non-negative");
        fieldError.setRejectedValue("-10");

        assertEquals("averageCost", fieldError.getField());
        assertEquals("must be non-negative", fieldError.getMessage());
        assertTrue(String.valueOf(fieldError.getRejectedValue()).contains("-10"));
    }
}