package com.nebula.identite.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nebula.identite.entity.Account;

public interface AccountRepository extends JpaRepository<Account, String> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Account> findByUsername(String username);
}
