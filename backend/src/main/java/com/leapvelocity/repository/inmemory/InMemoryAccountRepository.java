package com.leapvelocity.repository.inmemory;

import com.leapvelocity.entities.Account;
import com.leapvelocity.repository.AccountRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccountRepository implements AccountRepository {

	private final Map<Long, Account> accountsById;

	public InMemoryAccountRepository() {
		this.accountsById = new HashMap<>();
	}

	@Override
	public Optional<Account> findById(Long accountId) {
		return Optional.ofNullable(accountsById.get(accountId));
	}

	@Override
	public void save(Account account) {
		accountsById.put(account.getId(), account);
	}
}
