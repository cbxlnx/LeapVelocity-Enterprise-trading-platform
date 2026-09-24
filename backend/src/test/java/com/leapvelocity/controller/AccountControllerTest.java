package com.leapvelocity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AccountController REST endpoints.
 * Tests the GET /api/v1/accounts/{id}, /balance, /positions, and /orders endpoints.
 */
@DisplayName("AccountController")
class AccountControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AccountService accountService;

    private AccountDto testAccount;
    private AccountBalanceDto testBalance;
    private List<PositionDto> testPositions;
    private List<OrderDto> testOrders;

    @BeforeEach
    void setUp() {
        // Initialize mocks manually
        accountService = mock(AccountService.class);
        objectMapper = new ObjectMapper();

        // Setup MockMvc with controller and exception handler
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Setup test data
        testAccount = new AccountDto(
                1L,
                "ACC-001",
                "Alice Johnson",
                new BigDecimal("250000.00"),
                AccountStatus.ACTIVE,
                1,
                LocalDateTime.now()
        );

        testBalance = new AccountBalanceDto(
                1L,
                new BigDecimal("250000.00")
        );

        testPositions = List.of(
                new PositionDto(1L, 1L, "AAPL", new BigDecimal("100.00"), new BigDecimal("150.25")),
                new PositionDto(2L, 1L, "MSFT", new BigDecimal("50.00"), new BigDecimal("380.50"))
        );

        testOrders = List.of(
                new OrderDto(UUID.randomUUID(), 1L, "AAPL", OrderSide.BUY, new BigDecimal("100.00"), new BigDecimal("150.25"),
                        OrderStatus.FILLED, "IDEM-001", LocalDateTime.now()),
                new OrderDto(UUID.randomUUID(), 1L, "MSFT", OrderSide.SELL, new BigDecimal("50.00"), new BigDecimal("380.50"),
                        OrderStatus.FILLED, "IDEM-002", LocalDateTime.now().minusHours(1))
        );
    }

    // =============== GET /api/v1/accounts/{id} Tests ===============

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Successfully retrieves account details")
    void getAccountSuccessfullyRetrievesAccountDetails() throws Exception {
        // Arrange
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(1)))
                .andExpect(jsonPath("$.accountId", equalTo("ACC-001")))
                .andExpect(jsonPath("$.holderName", equalTo("Alice Johnson")))
                .andExpect(jsonPath("$.cashBalance", equalTo(250000.00)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.version", equalTo(1)))
                .andExpect(jsonPath("$.lastUpdated", notNullValue()));

        verify(accountService, times(1)).getAccount(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Returns HTTP 200 OK")
    void getAccountReturnsHttpOk() throws Exception {
        // Arrange
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Retrieves different account by ID")
    void getAccountRetrievesDifferentAccountsById() throws Exception {
        // Arrange
        AccountDto account2 = new AccountDto(
                2L,
                "ACC-002",
                "Bob Smith",
                new BigDecimal("500000.00"),
                AccountStatus.ACTIVE,
                1,
                LocalDateTime.now()
        );
        when(accountService.getAccount(2L)).thenReturn(account2);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/2")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(2)))
                .andExpect(jsonPath("$.accountId", equalTo("ACC-002")))
                .andExpect(jsonPath("$.holderName", equalTo("Bob Smith")))
                .andExpect(jsonPath("$.cashBalance", equalTo(500000.00)));

        verify(accountService, times(1)).getAccount(2L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Returns 404 when account not found")
    void getAccountReturns404WhenAccountNotFound() throws Exception {
        // Arrange
        when(accountService.getAccount(99L))
                .thenThrow(new AccountNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/99")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_001")));

        verify(accountService, times(1)).getAccount(99L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Response contains correct media type")
    void getAccountResponseHasCorrectMediaType() throws Exception {
        // Arrange
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Response contains all required fields")
    void getAccountResponseContainsAllRequiredFields() throws Exception {
        // Arrange
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.holderName").exists())
                .andExpect(jsonPath("$.cashBalance").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    // =============== GET /api/v1/accounts/{id}/balance Tests ===============

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - Successfully retrieves account balance")
    void getBalanceSuccessfullyRetrievesBalance() throws Exception {
        // Arrange
        when(accountService.getBalance(1L)).thenReturn(testBalance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/balance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", equalTo(1)))
                .andExpect(jsonPath("$.cashBalance", equalTo(250000.00)));

        verify(accountService, times(1)).getBalance(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - Returns HTTP 200 OK")
    void getBalanceReturnsHttpOk() throws Exception {
        // Arrange
        when(accountService.getBalance(1L)).thenReturn(testBalance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/balance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - Returns 404 when account not found")
    void getBalanceReturns404WhenAccountNotFound() throws Exception {
        // Arrange
        when(accountService.getBalance(99L))
                .thenThrow(new AccountNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/99/balance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_001")));

        verify(accountService, times(1)).getBalance(99L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - Response contains correct fields")
    void getBalanceResponseContainsCorrectFields() throws Exception {
        // Arrange
        when(accountService.getBalance(1L)).thenReturn(testBalance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/balance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.cashBalance").exists());
    }

    // =============== GET /api/v1/accounts/{id}/positions Tests ===============

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Successfully retrieves all positions")
    void getPositionsSuccessfullyRetrievesAllPositions() throws Exception {
        // Arrange
        when(accountService.getPositions(1L)).thenReturn(testPositions);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$[0].quantity", equalTo(100.00)))
                .andExpect(jsonPath("$[0].averageCost", equalTo(150.25)))
                .andExpect(jsonPath("$[1].symbol", equalTo("MSFT")))
                .andExpect(jsonPath("$[1].quantity", equalTo(50.00)))
                .andExpect(jsonPath("$[1].averageCost", equalTo(380.50)));

        verify(accountService, times(1)).getPositions(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Returns empty list when no positions")
    void getPositionsReturnsEmptyListWhenNoPositions() throws Exception {
        // Arrange
        when(accountService.getPositions(1L)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(accountService, times(1)).getPositions(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Returns HTTP 200 OK")
    void getPositionsReturnsHttpOk() throws Exception {
        // Arrange
        when(accountService.getPositions(1L)).thenReturn(testPositions);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Returns 404 when account not found")
    void getPositionsReturns404WhenAccountNotFound() throws Exception {
        // Arrange
        when(accountService.getPositions(99L))
                .thenThrow(new AccountNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/99/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_001")));

        verify(accountService, times(1)).getPositions(99L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Response is JSON array")
    void getPositionsResponseIsJsonArray() throws Exception {
        // Arrange
        when(accountService.getPositions(1L)).thenReturn(testPositions);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/positions - Position objects contain required fields")
    void getPositionsPositionObjectsContainRequiredFields() throws Exception {
        // Arrange
        when(accountService.getPositions(1L)).thenReturn(testPositions);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].accountId").exists())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].quantity").exists())
                .andExpect(jsonPath("$[0].averageCost").exists());
    }

    // =============== GET /api/v1/accounts/{id}/orders Tests ===============

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Successfully retrieves order history")
    void getOrdersSuccessfullyRetrievesOrderHistory() throws Exception {
        // Arrange
        when(accountService.getOrders(1L)).thenReturn(testOrders);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$[0].side", equalTo("BUY")))
                .andExpect(jsonPath("$[0].quantity", equalTo(100.00)))
                .andExpect(jsonPath("$[1].symbol", equalTo("MSFT")))
                .andExpect(jsonPath("$[1].side", equalTo("SELL")))
                .andExpect(jsonPath("$[1].quantity", equalTo(50.00)));

        verify(accountService, times(1)).getOrders(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Returns empty list when no orders")
    void getOrdersReturnsEmptyListWhenNoOrders() throws Exception {
        // Arrange
        when(accountService.getOrders(1L)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(accountService, times(1)).getOrders(1L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Returns HTTP 200 OK")
    void getOrdersReturnsHttpOk() throws Exception {
        // Arrange
        when(accountService.getOrders(1L)).thenReturn(testOrders);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Returns 404 when account not found")
    void getOrdersReturns404WhenAccountNotFound() throws Exception {
        // Arrange
        when(accountService.getOrders(99L))
                .thenThrow(new AccountNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/99/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_001")));

        verify(accountService, times(1)).getOrders(99L);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Response is JSON array")
    void getOrdersResponseIsJsonArray() throws Exception {
        // Arrange
        when(accountService.getOrders(1L)).thenReturn(testOrders);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/orders - Order objects contain required fields")
    void getOrdersOrderObjectsContainRequiredFields() throws Exception {
        // Arrange
        when(accountService.getOrders(1L)).thenReturn(testOrders);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].accountId").exists())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].side").exists())
                .andExpect(jsonPath("$[0].quantity").exists())
                .andExpect(jsonPath("$[0].price").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].idempotencyKey").exists())
                .andExpect(jsonPath("$[0].createdOn").exists());
    }

    // =============== Integration Tests ===============

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Multiple requests to different endpoints succeed")
    void multipleEndpointsSucceed() throws Exception {
        // Arrange
        when(accountService.getAccount(1L)).thenReturn(testAccount);
        when(accountService.getBalance(1L)).thenReturn(testBalance);
        when(accountService.getPositions(1L)).thenReturn(testPositions);
        when(accountService.getOrders(1L)).thenReturn(testOrders);

        // Act & Assert - Call all endpoints for the same account
        mockMvc.perform(get("/api/v1/accounts/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/1/balance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/1/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/1/orders")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(accountService, times(1)).getAccount(1L);
        verify(accountService, times(1)).getBalance(1L);
        verify(accountService, times(1)).getPositions(1L);
        verify(accountService, times(1)).getOrders(1L);
    }
}
