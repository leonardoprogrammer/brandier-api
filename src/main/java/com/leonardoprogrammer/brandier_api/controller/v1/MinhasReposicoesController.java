package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.V1 + "/reposicoes")
public class MinhasReposicoesController {

    private final ReposicaoService reposicaoService;
    private final CurrentUserResolver currentUserResolver;

    public MinhasReposicoesController(ReposicaoService reposicaoService, CurrentUserResolver currentUserResolver) {
        this.reposicaoService = reposicaoService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<PaginaDTO<ReposicaoDTO>> listarMinhas(@RequestParam(defaultValue = "0") int pagina,
                                                                @RequestParam(defaultValue = "20") int tamanho,
                                                                Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(reposicaoService.listarMinhas(usuario, pagina, tamanho));
    }
}
