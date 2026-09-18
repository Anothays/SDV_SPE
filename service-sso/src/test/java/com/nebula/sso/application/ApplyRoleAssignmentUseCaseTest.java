package com.nebula.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.sso.application.event.RoleAssignedEvent;
import com.nebula.sso.domain.Account;
import com.nebula.sso.domain.port.out.AccountPort;

class ApplyRoleAssignmentUseCaseTest {

    private AccountPort accountPort;
    private ApplyRoleAssignmentUseCase useCase;

    @BeforeEach
    void setUp() {
        accountPort = mock(AccountPort.class);
        useCase = new ApplyRoleAssignmentUseCase(accountPort);
    }

    private static Account playerAccount() {
        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setRole("PLAYER");
        return account;
    }

    @Test
    void updatesRoleWhenItDiffers() {
        when(accountPort.findById("uuid-1")).thenReturn(Optional.of(playerAccount()));

        useCase.execute(new RoleAssignedEvent("evt-1", 1, "2026-09-18T10:00:00Z", "uuid-1", "MODERATOR"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountPort).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("MODERATOR");
    }

    @Test
    void skipsWhenRoleAlreadyProjected_idempotence() {
        when(accountPort.findById("uuid-1")).thenReturn(Optional.of(playerAccount()));

        useCase.execute(new RoleAssignedEvent("evt-1", 1, "2026-09-18T10:00:00Z", "uuid-1", "PLAYER"));

        verify(accountPort, never()).save(any());
    }

    @Test
    void ignoresUnknownAccount() {
        when(accountPort.findById("inconnu")).thenReturn(Optional.empty());

        useCase.execute(new RoleAssignedEvent("evt-1", 1, "2026-09-18T10:00:00Z", "inconnu", "ADMIN"));

        verify(accountPort, never()).save(any());
    }

    @Test
    void rejectsEventWithoutPlayerIdOrRole() {
        assertThatThrownBy(() -> useCase.execute(
                new RoleAssignedEvent("evt-1", 1, "2026-09-18T10:00:00Z", null, "ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(
                new RoleAssignedEvent("evt-2", 1, "2026-09-18T10:00:00Z", "uuid-1", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accountPort, never()).save(any());
    }

    @Test
    void rejectsUnknownRole_neverReachesJwtClaim() {
        when(accountPort.findById("uuid-1")).thenReturn(Optional.of(playerAccount()));

        assertThatThrownBy(() -> useCase.execute(
                new RoleAssignedEvent("evt-3", 1, "2026-09-18T10:00:00Z", "uuid-1", "KING")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accountPort, never()).save(any());
    }
}
