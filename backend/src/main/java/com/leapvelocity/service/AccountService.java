package com.leapvelocity.service;

import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.entities.Account;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.mapper.AccountMapper;
import com.leapvelocity.mapper.OrderMapper;
import com.leapvelocity.mapper.PositionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;

    public AccountService(AccountMapper accountMapper, PositionMapper positionMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
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
        return positionMapper.findByAccountId(id).stream()
                .map(PositionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrders(Long id) {
        requireAccount(id);
        return orderMapper.findByAccountId(id).stream()
                .map(OrderDto::from)
                .toList();
    }

    private Account requireAccount(Long id) {
        if (id == null) {
            throw new AccountNotFoundException("Account id is required");
        }
        return accountMapper.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
