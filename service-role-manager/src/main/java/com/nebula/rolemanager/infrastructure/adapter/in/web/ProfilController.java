package com.nebula.rolemanager.infrastructure.adapter.in.web;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nebula.rolemanager.application.ProfilService;
import com.nebula.rolemanager.application.dto.ProfilDto;
import com.nebula.rolemanager.application.dto.UpdateProfilRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profils")
public class ProfilController {

    private final ProfilService profilService;

    public ProfilController(ProfilService profilService) {
        this.profilService = profilService;
    }

    // La création n'est plus exposée : un profil naît par événement
    // players.registered (spec §5), jamais par POST direct.

    @GetMapping("/{playerId}")
    @Transactional(readOnly = true)
    public ProfilDto get(@PathVariable String playerId) {
        return profilService.findByPlayerId(playerId);
    }

    @PutMapping("/{playerId}")
    @Transactional
    public ProfilDto update(@PathVariable String playerId,
            @Valid @RequestBody UpdateProfilRequest request) {
        return profilService.updateRegion(playerId, request.region());
    }
}
