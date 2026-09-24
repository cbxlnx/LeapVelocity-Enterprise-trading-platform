package com.leapvelocity.service;

import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.entities.Account;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    public AccountService(AccountRepository accountRepository, PositionRepository positionRepository, OrderRepository orderRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(Long id) {
        return AccountDto.from(requireAccount(id));
    }

    @Transactional(readOnly = true)
    public AccountBalanceDto getBalance(Long id) {
        Account account = requireAccount(id);
        return new AccountBalanceDto(account.getId(), account.getCashBalance());
    }

    @Transactional(readOnly = true)
    public List<PositionDto> getPositions(Long id) {
        requireAccount(id);
        return positionRepository.findByAccountIdOrderBySymbolAsc(id).stream()
                .map(PositionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrders(Long id) {
        requireAccount(id);
        return orderRepository.findByAccountIdOrderByCreatedOnDescIdDesc(id).stream()
                .map(OrderDto::from)
                .toList();
    }

    private Account requireAccount(Long id) {
        if (id == null) {
            throw new AccountNotFoundException("Account id is required");
        }
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
