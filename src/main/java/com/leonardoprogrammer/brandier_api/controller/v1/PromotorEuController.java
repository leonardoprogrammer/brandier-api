package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.MeusVinculosDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PromotorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Meus vínculos", description = "Marcas em que o usuário autenticado é promotor.")
@RestController
@RequestMapping(ApiVersion.V1 + "/promotores/eu")
public class PromotorEuController {

    private final PromotorService promotorService;
    private final CurrentUserResolver currentUserResolver;

    public PromotorEuController(PromotorService promotorService, CurrentUserResolver currentUserResolver) {
        this.promotorService = promotorService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista as marcas em que sou promotor")
    @GetMapping
    public ResponseEntity<MeusVinculosDTO> meusVinculos(Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(new MeusVinculosDTO(promotorService.meusVinculos(usuario)));
    }

    @Operation(summary = "Sai de uma marca como promotor", description = "As reposições já registradas continuam existindo; só o vínculo é removido.")
    @DeleteMapping("/{marcaId}")
    public ResponseEntity<Void> sair(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        promotorService.sair(usuario, marcaId);
        return ResponseEntity.noContent().build();
    }
}
