package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.RelatorioDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.RelatorioService;
import com.leonardoprogrammer.brandier_api.service.RelatorioService.ArquivoCsv;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Relatórios", description = "Relatório de reposições de uma marca com filtros combináveis, paginação e exportação CSV. Gestor vê tudo; promotor tem o `autorId` forçado para si mesmo.")
@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/relatorios/reposicoes")
public class RelatorioController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RelatorioService relatorioService;
    private final CurrentUserResolver currentUserResolver;

    public RelatorioController(RelatorioService relatorioService, CurrentUserResolver currentUserResolver) {
        this.relatorioService = relatorioService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Consulta o relatório de reposições", description = "Filtros opcionais combináveis (loja, autor, produto, período); devolve a página pedida e os totais do filtro inteiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PERIODO_INVALIDO"),
    })
    @GetMapping
    public ResponseEntity<RelatorioDTO> consultar(
            @PathVariable UUID marcaId,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) UUID autorId,
            @RequestParam(required = false) UUID produtoId,
            @Parameter(description = "Início do período (inclusive, ISO 8601); sem filtro = todo o período") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim do período (exclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @Parameter(description = "data, loja ou autor") @RequestParam(defaultValue = "data") String ordenar,
            @Parameter(description = "asc ou desc") @RequestParam(defaultValue = "desc") String direcao,
            Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(relatorioService.consultar(usuario, marcaId, lojaId, autorId, produtoId, de, ate,
                pagina, tamanho, ordenar, direcao));
    }

    @Operation(summary = "Exporta o relatório em CSV", description = "Mesmos filtros da consulta, sem paginação; uma linha por item, UTF-8 com BOM, separador `;`, protegido contra injeção de fórmula. Acima de 5.000 reposições, recuse com RELATORIO_MUITO_GRANDE e peça para refinar os filtros.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PERIODO_INVALIDO ou RELATORIO_MUITO_GRANDE"),
            @ApiResponse(responseCode = "200", description = "Arquivo CSV (`Content-Disposition: attachment`)", content = @Content(mediaType = "text/csv")),
    })
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportar(
            @PathVariable UUID marcaId,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) UUID autorId,
            @RequestParam(required = false) UUID produtoId,
            @Parameter(description = "Início do período (inclusive, ISO 8601); sem filtro = todo o período") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Fim do período (exclusive, ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @Parameter(description = "data, loja ou autor") @RequestParam(defaultValue = "data") String ordenar,
            @Parameter(description = "asc ou desc") @RequestParam(defaultValue = "desc") String direcao,
            Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        ArquivoCsv arquivo = relatorioService.exportar(usuario, marcaId, lojaId, autorId, produtoId, de, ate, ordenar, direcao);

        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.conteudo());
    }
}
