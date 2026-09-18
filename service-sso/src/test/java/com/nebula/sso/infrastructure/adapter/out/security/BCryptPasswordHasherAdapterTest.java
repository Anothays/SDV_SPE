package com.nebula.sso.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHasherAdapterTest {

    private final BCryptPasswordHasherAdapter adapter =
            new BCryptPasswordHasherAdapter(new BCryptPasswordEncoder());

    @Test
    void hashThenMatchesRoundTrips() {
        String hash = adapter.hash("s3cret!");

        assertThat(hash).isNotEqualTo("s3cret!");
        assertThat(adapter.matches("s3cret!", hash)).isTrue();
        assertThat(adapter.matches("wrong", hash)).isFalse();
    }
}
