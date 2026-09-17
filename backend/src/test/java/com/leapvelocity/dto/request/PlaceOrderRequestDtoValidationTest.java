package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.OrderSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlaceOrderRequestDto validation")
class adPlaceOrderRequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    @DisplayName("valid request has no validation errors")
    void validRequestHasNoViolations() {
        PlaceOrderRequestDto request = new PlaceOrderRequestDto(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("150.00"),
                "order-001"
        );

        Set<ConstraintViolation<PlaceOrderRequestDto>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("blank, null, and zero values are rejected")
    void invalidRequestRejectsBlankNullAndZeroValues() {
        PlaceOrderRequestDto request = new PlaceOrderRequestDto(
                1L,
                " ",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                " "
        );

        Set<String> invalidFields = invalidFieldsFor(request);

        assertEquals(Set.of("symbol", "side", "quantity", "price", "idempotencyKey"), invalidFields);
    }

    @Test
    @DisplayName("negative quantity and price are rejected")
    void invalidRequestRejectsNegativeQuantityAndPrice() {
        PlaceOrderRequestDto request = new PlaceOrderRequestDto(
                1L,
                "AAPL",
                OrderSide.SELL,
                new BigDecimal("-1"),
                new BigDecimal("-150.00"),
                "order-002"
        );

        Set<String> invalidFields = invalidFieldsFor(request);

        assertEquals(Set.of("quantity", "price"), invalidFields);
    }

    private Set<String> invalidFieldsFor(PlaceOrderRequestDto request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
