package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DTO mapping")
class DtoMappingTest {

    @Test
    @DisplayName("account dto copies account fields")
    void accountDtoCopiesAccountFields() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        account.setId(1L);

        AccountDto dto = AccountDto.from(account);

        assertEquals(1L, dto.id());
        assertEquals("ACC-001", dto.accountId());
        assertEquals("Ada Lovelace", dto.holderName());
        assertEquals(new BigDecimal("1000.00"), dto.cashBalance());
        assertEquals(AccountStatus.ACTIVE, dto.status());
        assertEquals(0L, dto.version());
        assertNotNull(dto.lastUpdated());
    }

    @Test
    @DisplayName("instrument dto copies instrument fields")
    void instrumentDtoCopiesInstrumentFields() {
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        instrument.setId(2L);

        InstrumentDto dto = InstrumentDto.from(instrument);

        assertEquals(2L, dto.id());
        assertEquals("AAPL", dto.symbol());
        assertEquals("Apple Inc.", dto.name());
        assertEquals("EQUITY", dto.assetClass());
        assertEquals("USD", dto.currency());
        assertTrue(dto.tradable());
    }

    @Test
    @DisplayName("order dto copies order fields")
    void orderDtoCopiesOrderFields() {
        Order order = new Order(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("150.00"),
                "idem-001"
        );
        order.setId(3L);
        order.setStatus(OrderStatus.FILLED);

        OrderDto dto = OrderDto.from(order);

        assertEquals(3L, dto.id());
        assertEquals(1L, dto.accountId());
        assertEquals("AAPL", dto.symbol());
        assertEquals(OrderSide.BUY, dto.side());
        assertEquals(new BigDecimal("10"), dto.quantity());
        assertEquals(new BigDecimal("150.00"), dto.price());
        assertEquals(OrderStatus.FILLED, dto.status());
        assertEquals("idem-001", dto.idempotencyKey());
        assertNotNull(dto.createdOn());
    }

    @Test
    @DisplayName("position dto copies position fields")
    void positionDtoCopiesPositionFields() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));
        position.setId(4L);

        PositionDto dto = PositionDto.from(position);

        assertEquals(4L, dto.id());
        assertEquals(1L, dto.accountId());
        assertEquals("AAPL", dto.symbol());
        assertEquals(new BigDecimal("10"), dto.quantity());
        assertEquals(new BigDecimal("100.00"), dto.averageCost());
    }
}
