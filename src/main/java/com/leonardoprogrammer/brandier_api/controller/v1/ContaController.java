package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.RequestAlterarSenhaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestAtualizarNomeDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestExcluirContaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.UsuarioDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ContaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.V1 + "/usuario")
public class ContaController {

    private final ContaService contaService;
    private final CurrentUserResolver currentUserResolver;
    private final AuthRateLimiter rateLimiter;
    private final AuthCookies authCookies;

    public ContaController(ContaService contaService,
                           CurrentUserResolver currentUserResolver,
                           AuthRateLimiter rateLimiter,
                           AuthCookies authCookies) {
        this.contaService = contaService;
        this.currentUserResolver = currentUserResolver;
        this.rateLimiter = rateLimiter;
        this.authCookies = authCookies;
    }

    @PostMapping("/excluir-conta")
    public ResponseEntity<Void> excluir(@Valid @RequestBody RequestExcluirContaDTO requisicao,
                                        Authentication authentication,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        rateLimiter.checkAccountDeletion(usuario.getId().toString());
        contaService.excluir(usuario, requisicao.senha());
        authCookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    public ResponseEntity<UsuarioDTO> atualizarNome(@Valid @RequestBody RequestAtualizarNomeDTO requisicao,
                                                     Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        Usuario atualizado = contaService.atualizarNome(usuario, requisicao.nome());
        return ResponseEntity.ok(paraDTO(atualizado));
    }

    @PostMapping("/aceitar-termos")
    public ResponseEntity<UsuarioDTO> aceitarTermos(Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        Usuario atualizado = contaService.aceitarTermos(usuario);
        return ResponseEntity.ok(paraDTO(atualizado));
    }

    private UsuarioDTO paraDTO(Usuario usuario) {
        return new UsuarioDTO(usuario.getId().toString(), usuario.getNome(), usuario.getEmail(),
                usuario.isSenhaDefinida(), usuario.getTermosAceitosEm() != null);
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(@Valid @RequestBody RequestAlterarSenhaDTO requisicao,
                                             Authentication authentication,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        rateLimiter.checkPasswordChange(usuario.getId().toString());
        contaService.alterarSenha(usuario, requisicao.senhaAtual(), requisicao.novaSenha());
        authCookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }
}
