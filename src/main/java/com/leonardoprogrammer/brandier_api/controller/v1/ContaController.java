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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Usuário")
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

    @Operation(summary = "Exclui a própria conta", description = "Por anonimização, não por DELETE: e-mail e senha são apagados/invalidados (o nome é mantido nas reposições já registradas, para rastreabilidade administrativa); marcas próprias sem histórico são apagadas, com histórico são inativadas. Senha obrigatória só se a conta tiver uma senha definida (contas só-Google não pedem senha, só a confirmação).")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "SENHA_INCORRETA"),
    })
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

    @Operation(summary = "Atualiza o nome da conta", description = "Não exige senha, sem limite de tentativas e não afeta a sessão.")
    @PutMapping
    public ResponseEntity<UsuarioDTO> atualizarNome(@Valid @RequestBody RequestAtualizarNomeDTO requisicao,
                                                     Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        Usuario atualizado = contaService.atualizarNome(usuario, requisicao.nome());
        return ResponseEntity.ok(paraDTO(atualizado));
    }

    @Operation(summary = "Aceita os termos de uso", description = "Idempotente: contas criadas por e-mail/senha já nascem com os termos aceitos; contas criadas pelo Google nascem sem aceitar, ficam bloqueadas do painel no front até chamar esta rota.")
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

    @Operation(summary = "Altera (ou define) a senha", description = "Troca o hash, revoga todos os refresh tokens e limpa os cookies de sessão (o usuário precisa entrar de novo). Senha atual obrigatória só se a conta já tiver uma senha definida; caso contrário define a primeira senha (contas criadas pelo Google).")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "SENHA_INCORRETA"),
    })
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
