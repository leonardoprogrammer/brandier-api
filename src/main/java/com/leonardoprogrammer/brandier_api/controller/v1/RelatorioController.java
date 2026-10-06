package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.RelatorioDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.RelatorioService;
import com.leonardoprogrammer.brandier_api.service.RelatorioService.ArquivoCsv;
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

    @GetMapping
    public ResponseEntity<RelatorioDTO> consultar(
            @PathVariable UUID marcaId,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) UUID autorId,
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(defaultValue = "data") String ordenar,
            @RequestParam(defaultValue = "desc") String direcao,
            Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(relatorioService.consultar(usuario, marcaId, lojaId, autorId, produtoId, de, ate,
                pagina, tamanho, ordenar, direcao));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportar(
            @PathVariable UUID marcaId,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) UUID autorId,
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(defaultValue = "data") String ordenar,
            @RequestParam(defaultValue = "desc") String direcao,
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
