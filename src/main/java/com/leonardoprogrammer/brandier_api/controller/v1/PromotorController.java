package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PromotoresDaMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PromotorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/promotores")
public class PromotorController {

    private final PromotorService promotorService;
    private final CurrentUserResolver currentUserResolver;

    public PromotorController(PromotorService promotorService, CurrentUserResolver currentUserResolver) {
        this.promotorService = promotorService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<PromotoresDaMarcaDTO> listar(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(PromotoresDaMarcaDTO.de(promotorService.listarDaMarca(gestor, marcaId)));
    }

    @DeleteMapping("/{promotorId}")
    public ResponseEntity<Void> remover(@PathVariable UUID marcaId, @PathVariable UUID promotorId,
                                        Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        promotorService.remover(gestor, marcaId, promotorId);
        return ResponseEntity.noContent().build();
    }
}
