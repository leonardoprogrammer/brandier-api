package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PainelAutorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelGestorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PainelService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
public class PainelController {

    private final PainelService painelService;
    private final CurrentUserResolver currentUserResolver;

    public PainelController(PainelService painelService, CurrentUserResolver currentUserResolver) {
        this.painelService = painelService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/painel/gestor")
    public ResponseEntity<PainelGestorDTO> painelDoGestor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDoGestor(gestor, de, ate));
    }

    @GetMapping("/painel/minhas-reposicoes")
    public ResponseEntity<PainelAutorDTO> painelDasMinhasReposicoes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario autor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDasMinhasReposicoes(autor, de, ate));
    }

    @GetMapping("/marcas/{marcaId}/painel")
    public ResponseEntity<PainelMarcaDTO> painelDaMarca(
            @PathVariable UUID marcaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDaMarca(gestor, marcaId, de, ate));
    }
}
