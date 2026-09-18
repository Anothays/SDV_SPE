package com.nebula.identite.domain.port.out;

import java.util.Optional;

import com.nebula.identite.domain.Account;

public interface AccountPort {

    Account save(Account account);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Account> findByUsername(String username);
}
