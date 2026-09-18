package com.nebula.rolemanager.infrastructure.adapter.in.web;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nebula.rolemanager.application.ChangeRoleUseCase;
import com.nebula.rolemanager.application.RoleAssignmentQueryService;
import com.nebula.rolemanager.application.dto.ChangeRoleRequest;
import com.nebula.rolemanager.application.dto.RoleAssignmentDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleAssignmentQueryService queryService;
    private final ChangeRoleUseCase changeRoleUseCase;

    public RoleController(RoleAssignmentQueryService queryService, ChangeRoleUseCase changeRoleUseCase) {
        this.queryService = queryService;
        this.changeRoleUseCase = changeRoleUseCase;
    }

    // Pas de POST : une attribution naît par événement players.registered
    // (spec §3.3), jamais par création directe.

    @GetMapping("/{playerId}")
    @Transactional(readOnly = true)
    public RoleAssignmentDto get(@PathVariable String playerId) {
        return queryService.findByPlayerId(playerId);
    }

    // Réservé au rôle ADMIN (SecurityConfig). Transaction portée ici : le use
    // case écrit l'attribution et la ligne outbox atomiquement.
    @PutMapping("/{playerId}")
    @Transactional
    public RoleAssignmentDto changeRole(@PathVariable String playerId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return changeRoleUseCase.execute(playerId, request.role());
    }
}
