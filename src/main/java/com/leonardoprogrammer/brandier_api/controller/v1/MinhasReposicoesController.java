package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reposições")
@RestController
@RequestMapping(ApiVersion.V1 + "/reposicoes")
public class MinhasReposicoesController {

    private final ReposicaoService reposicaoService;
    private final CurrentUserResolver currentUserResolver;

    public MinhasReposicoesController(ReposicaoService reposicaoService, CurrentUserResolver currentUserResolver) {
        this.reposicaoService = reposicaoService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista minhas reposições em todas as marcas, paginado", description = "As reposições do próprio usuário autenticado como autor, em qualquer marca em que seja promotor ou gestor.")
    @GetMapping
    public ResponseEntity<PaginaDTO<ReposicaoDTO>> listarMinhas(@RequestParam(defaultValue = "0") int pagina,
                                                                @RequestParam(defaultValue = "20") int tamanho,
                                                                Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(reposicaoService.listarMinhas(usuario, pagina, tamanho));
    }
}
