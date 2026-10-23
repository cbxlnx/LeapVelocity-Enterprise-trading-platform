package com.leapvelocity.messaging;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.service.OrderExecutionService;
import com.leapvelocity.service.PositionUpdateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderDeadLetterTest {

    @Test
    void listenerPassesDeadLetteredOrderIdToService() throws Exception {
        OrderExecutionService service = mock(OrderExecutionService.class);
        UUID orderId = UUID.randomUUID();
        String deadLetter = "{\"originalTopic\":\"orders\",\"originalOffset\":0,\"originalPartition\":0,"
                + "\"failureReason\":\"TEMPORARY_FAILURE_RETRIES_EXHAUSTED: No marketable BUY price\","
                + "\"message\":\"{\\\"orderId\\\":\\\"" + orderId + "\\\",\\\"accountId\\\":7}\"}";

        new OrderDeadLetterListener(service).onDeadLetter(deadLetter);

        verify(service).rejectDeadLetteredOrder(orderId);
    }

    @Test
    void deadLetteredNewOrderIsRejected() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = new Order(7L, "AAPL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("345.54"), "key-1");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        serviceWith(orderRepository).rejectDeadLetteredOrder(order.getId());

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void deadLetterDoesNotChangeFilledOrder() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = new Order(7L, "AAPL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("355.00"), "key-2");
        order.setStatus(OrderStatus.FILLED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        serviceWith(orderRepository).rejectDeadLetteredOrder(order.getId());

        assertEquals(OrderStatus.FILLED, order.getStatus());
        verify(orderRepository, never()).save(any());
    }

    private OrderExecutionService serviceWith(OrderRepository orderRepository) {
        return new OrderExecutionService(mock(AccountRepository.class), mock(InstrumentRepository.class),
                orderRepository, mock(ExecutionRepository.class), mock(PositionUpdateService.class));
    }
}
