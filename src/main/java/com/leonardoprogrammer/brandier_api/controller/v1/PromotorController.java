package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PromotoresDaMarcaDTO;
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

@Tag(name = "Promotores", description = "Promotores vinculados a uma marca, do ponto de vista do gestor (ver também \"Convites\" e \"Meus vínculos\").")
@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/promotores")
public class PromotorController {

    private final PromotorService promotorService;
    private final CurrentUserResolver currentUserResolver;

    public PromotorController(PromotorService promotorService, CurrentUserResolver currentUserResolver) {
        this.promotorService = promotorService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista promotores e convites da marca", description = "Só o gestor. Traz os promotores vinculados e os convites (pendente/aceito/recusado/expirado) junto com o uso do limite de vagas.")
    @GetMapping
    public ResponseEntity<PromotoresDaMarcaDTO> listar(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(PromotoresDaMarcaDTO.de(promotorService.listarDaMarca(gestor, marcaId)));
    }

    @Operation(summary = "Remove um promotor da marca", description = "Só o gestor. As reposições já registradas pelo promotor continuam existindo; só o vínculo é removido.")
    @DeleteMapping("/{promotorId}")
    public ResponseEntity<Void> remover(@PathVariable UUID marcaId, @PathVariable UUID promotorId,
                                        Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        promotorService.remover(gestor, marcaId, promotorId);
        return ResponseEntity.noContent().build();
    }
}
