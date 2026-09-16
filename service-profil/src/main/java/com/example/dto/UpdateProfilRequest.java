package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Seule la région est modifiable par le joueur (spec §5). */
public record UpdateProfilRequest(
        @NotBlank @Pattern(regexp = "EU|NA|ASIA") String region) {
}
