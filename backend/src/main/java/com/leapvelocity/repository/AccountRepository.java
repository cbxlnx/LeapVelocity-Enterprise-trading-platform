package com.leapvelocity.repository;

import com.leapvelocity.entities.Account;

import java.util.Optional;

public interface AccountRepository {

	Optional<Account> findById(Long accountId);

	void save(Account account);
}
