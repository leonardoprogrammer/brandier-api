package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PainelAutorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelGestorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Painel", description = "Agregações para as duas visões do painel: como gestor e como autor de reposições. Período padrão: últimos 30 dias (o front sempre envia `de`/`ate` explicitamente).")
@RestController
@RequestMapping(ApiVersion.V1)
public class PainelController {

    private final PainelService painelService;
    private final CurrentUserResolver currentUserResolver;

    public PainelController(PainelService painelService, CurrentUserResolver currentUserResolver) {
        this.painelService = painelService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Painel do gestor", description = "Totais do período por marca gerida, uso de lojas/produtos/promotores e o limite de marcas.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PERIODO_INVALIDO"),
    })
    @GetMapping("/painel/gestor")
    public ResponseEntity<PainelGestorDTO> painelDoGestor(
            @Parameter(description = "Início do período (inclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim do período (exclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDoGestor(gestor, de, ate));
    }

    @Operation(summary = "Painel das minhas reposições", description = "Totais do período como autor, em qualquer marca (inclusive as registradas pelo gestor na própria marca), por marca/loja/produto e as 5 últimas.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PERIODO_INVALIDO"),
    })
    @GetMapping("/painel/minhas-reposicoes")
    public ResponseEntity<PainelAutorDTO> painelDasMinhasReposicoes(
            @Parameter(description = "Início do período (inclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim do período (exclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario autor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDasMinhasReposicoes(autor, de, ate));
    }

    @Operation(summary = "Painel de uma marca", description = "Só o gestor: totais do período, por loja, por promotor, por produto, promotores sem atividade e uso dos limites.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PERIODO_INVALIDO"),
    })
    @GetMapping("/marcas/{marcaId}/painel")
    public ResponseEntity<PainelMarcaDTO> painelDaMarca(
            @PathVariable UUID marcaId,
            @Parameter(description = "Início do período (inclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim do período (exclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(painelService.painelDaMarca(gestor, marcaId, de, ate));
    }
}
