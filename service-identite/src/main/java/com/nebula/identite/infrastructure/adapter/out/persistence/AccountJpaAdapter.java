package com.nebula.identite.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.nebula.identite.domain.Account;
import com.nebula.identite.domain.port.out.AccountPort;

@Repository
public class AccountJpaAdapter implements AccountPort {

    private final AccountRepository accountRepository;

    public AccountJpaAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Account save(Account account) {
        AccountEntity saved = accountRepository.save(toEntity(account));
        return toDomain(saved);
    }

    @Override
    public boolean existsByUsername(String username) {
        return accountRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return accountRepository.existsByEmail(email);
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return accountRepository.findByUsername(username).map(AccountJpaAdapter::toDomain);
    }

    private static AccountEntity toEntity(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.setId(account.getId());
        entity.setUsername(account.getUsername());
        entity.setEmail(account.getEmail());
        entity.setPasswordHash(account.getPasswordHash());
        entity.setRole(account.getRole());
        entity.setRegion(account.getRegion());
        entity.setCreatedAt(account.getCreatedAt());
        return entity;
    }

    private static Account toDomain(AccountEntity entity) {
        Account account = new Account();
        account.setId(entity.getId());
        account.setUsername(entity.getUsername());
        account.setEmail(entity.getEmail());
        account.setPasswordHash(entity.getPasswordHash());
        account.setRole(entity.getRole());
        account.setRegion(entity.getRegion());
        account.setCreatedAt(entity.getCreatedAt());
        return account;
    }
}
