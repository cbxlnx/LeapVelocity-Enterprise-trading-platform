package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.OrderStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case NEW -> "PENDING";
            case FILLED -> "SUCCESSFUL";
            case REJECTED -> "REJECTED";
            case CANCELLED -> "CANCELLED";
        };
    }

    @Override
    public OrderStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "PENDING" -> OrderStatus.NEW;
            case "SUCCESSFUL" -> OrderStatus.FILLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unknown order status: " + value);
        };
    }
}
