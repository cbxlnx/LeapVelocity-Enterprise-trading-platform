package com.leapvelocity.service;

import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AccountService")
class AccountServiceTest {

    private RepositoryMocks mocks;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private OrderRepository orderRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        mocks = new RepositoryMocks();
        accountRepository = mocks.accountRepository();
        positionRepository = mocks.positionRepository();
        orderRepository = mocks.orderRepository();
        accountService = new AccountService(accountRepository, positionRepository, orderRepository);
    }

    @Test
    @DisplayName("getAccount returns account details")
    void getAccountReturnsAccountDetails() {
        mocks.accounts.put(1L, account(1L));

        AccountDto result = accountService.getAccount(1L);

        assertEquals(1L, result.id());
        assertEquals("ACC-001", result.accountId());
        assertEquals("Alice Johnson", result.holderName());
        assertEquals(new BigDecimal("250000.00"), result.cashBalance());
        assertEquals(AccountStatus.ACTIVE, result.status());
    }

    @Test
    @DisplayName("getBalance returns account cash balance")
    void getBalanceReturnsAccountCashBalance() {
        mocks.accounts.put(1L, account(1L));

        AccountBalanceDto result = accountService.getBalance(1L);

        assertEquals(1L, result.accountId());
        assertEquals(new BigDecimal("250000.00"), result.cashBalance());
    }

    @Test
    @DisplayName("getPositions returns positions for existing account")
    void getPositionsReturnsPositionsForExistingAccount() {
        mocks.accounts.put(1L, account(1L));
        mocks.positions.put(1L, List.of(new Position(1L, "GLBEQ1", new BigDecimal("100.0000"), new BigDecimal("12.50"))));

        List<PositionDto> result = accountService.getPositions(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).accountId());
        assertEquals("GLBEQ1", result.get(0).symbol());
        assertEquals(new BigDecimal("100.0000"), result.get(0).quantity());
        assertEquals(1, mocks.positionQueries);
    }

    @Test
    @DisplayName("getOrders returns order history for existing account")
    void getOrdersReturnsOrderHistoryForExistingAccount() {
        mocks.accounts.put(1L, account(1L));
        mocks.orders.put(1L, List.of(new Order(1L, "GLBEQ1", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("12.50"), "IDEM-001")));

        List<OrderDto> result = accountService.getOrders(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).accountId());
        assertEquals("GLBEQ1", result.get(0).symbol());
        assertEquals(OrderSide.BUY, result.get(0).side());
        assertEquals(1, mocks.orderQueries);
    }

    @Test
    @DisplayName("throws AccountNotFoundException when account does not exist")
    void throwsWhenAccountDoesNotExist() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(99L));
    }

    @Test
    @DisplayName("does not query related data when account does not exist")
    void doesNotQueryRelatedDataWhenAccountDoesNotExist() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getPositions(99L));
        assertThrows(AccountNotFoundException.class, () -> accountService.getOrders(99L));
        assertEquals(0, mocks.positionQueries);
        assertEquals(0, mocks.orderQueries);
    }

    private Account account(Long id) {
        Account account = new Account("ACC-%03d".formatted(id), "Alice Johnson",
                new BigDecimal("250000.00"), AccountStatus.ACTIVE);
        account.setId(id);
        return account;
    }

    private static class RepositoryMocks {
        private final Map<Long, Account> accounts = new HashMap<>();
        private final Map<Long, List<Position>> positions = new HashMap<>();
        private final Map<Long, List<Order>> orders = new HashMap<>();
        private int positionQueries;
        private int orderQueries;

        private AccountRepository accountRepository() {
            return proxy(AccountRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findById")) {
                    return Optional.ofNullable(accounts.get(args[0]));
                }
                return unsupported(method);
            });
        }

        private PositionRepository positionRepository() {
            return proxy(PositionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findByAccountIdOrderBySymbolAsc")) {
                    positionQueries++;
                    return positions.getOrDefault(args[0], List.of());
                }
                return unsupported(method);
            });
        }

        private OrderRepository orderRepository() {
            return proxy(OrderRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findByAccountIdOrderByCreatedOnDescIdDesc")) {
                    orderQueries++;
                    return orders.getOrDefault(args[0], List.of());
                }
                return unsupported(method);
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Object unsupported(Method method) {
            throw new UnsupportedOperationException("Unexpected repository call: " + method.getName());
        }
    }
}
