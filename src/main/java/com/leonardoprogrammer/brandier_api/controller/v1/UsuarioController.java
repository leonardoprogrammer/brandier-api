package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.model.dto.*;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.security.JwtService;
import com.leonardoprogrammer.brandier_api.security.TurnstileService;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.service.CadastroService;
import com.leonardoprogrammer.brandier_api.service.GoogleLoginService;
import com.leonardoprogrammer.brandier_api.service.RefreshTokenService;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuário")
@RestController
@RequestMapping(ApiVersion.V1 + "/usuario")
public class UsuarioController {

    private static final String OPERATION_VERIFY_EMAIL = "verify-email";
    private static final String OPERATION_RESEND_VERIFICATION = "resend-verification";

    private final JwtService jwtService;
    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookies authCookies;
    private final CadastroService cadastroService;
    private final AuthRateLimiter rateLimiter;
    private final TurnstileService turnstileService;
    private final GoogleLoginService googleLoginService;

    public UsuarioController(JwtService jwtService,
                             UsuarioService usuarioService,
                             RefreshTokenService refreshTokenService,
                             AuthCookies authCookies,
                             CadastroService cadastroService,
                             AuthRateLimiter rateLimiter,
                             TurnstileService turnstileService,
                             GoogleLoginService googleLoginService) {
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
        this.cadastroService = cadastroService;
        this.rateLimiter = rateLimiter;
        this.turnstileService = turnstileService;
        this.googleLoginService = googleLoginService;
    }

    @Operation(summary = "Cria uma conta", description = "Resposta 202 genérica, exista ou não conta com esse e-mail (sem enumeração). Exige aceitar os termos (`aceitouTermos: true`); a conta nasce PENDENTE até verificar o e-mail. Protegido por Cloudflare Turnstile quando habilitado.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMAIL_DESCARTAVEL ou CAPTCHA_INVALIDO"),
            @ApiResponse(responseCode = "409", description = "LIMITE_CONTAS_ATINGIDO"),
    })
    @PostMapping
    public ResponseEntity<ResponseMessageDTO> cadastro(@Valid @RequestBody RequestCadastroDTO requestCadastroDTO,
                                                       HttpServletRequest request) {
        rateLimiter.checkSignup(request.getRemoteAddr());
        turnstileService.verify(requestCadastroDTO.getTurnstileToken(), request.getRemoteAddr());
        try {
            cadastroService.cadastrar(requestCadastroDTO.getNome(), requestCadastroDTO.getEmail(), requestCadastroDTO.getSenha());
        } catch (DataIntegrityViolationException e) {
            return respostaCadastro();
        }
        return respostaCadastro();
    }

    @Operation(summary = "Verifica o e-mail do cadastro", description = "Token opaco de uso único, válido por `brandier.verification.token-expiration-hours` (24h). Ativa a conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "TOKEN_VERIFICACAO_INVALIDO"),
    })
    @PostMapping("/verificar-email")
    public ResponseEntity<ResponseMessageDTO> verificarEmail(@Valid @RequestBody RequestVerificarEmailDTO requestVerificarEmailDTO,
                                                             HttpServletRequest request) {
        rateLimiter.checkToken(OPERATION_VERIFY_EMAIL, request.getRemoteAddr());
        cadastroService.verificarEmail(requestVerificarEmailDTO.getToken());
        return ResponseEntity.ok(new ResponseMessageDTO("E-mail verificado com sucesso. Você já pode entrar."));
    }

    @Operation(summary = "Reenvia o e-mail de verificação", description = "Resposta 202 genérica, exista ou não conta pendente com esse e-mail; respeita o intervalo mínimo entre reenvios.")
    @PostMapping("/reenviar-verificacao")
    public ResponseEntity<ResponseMessageDTO> reenviarVerificacao(@Valid @RequestBody RequestReenviarVerificacaoDTO requestReenviarVerificacaoDTO,
                                                                  HttpServletRequest request) {
        rateLimiter.checkRecovery(OPERATION_RESEND_VERIFICATION, request.getRemoteAddr(),
                EmailCanonicalizer.canonicalize(requestReenviarVerificacaoDTO.getEmail()));
        cadastroService.reenviarVerificacao(requestReenviarVerificacaoDTO.getEmail());
        return respostaCadastro();
    }

    @Operation(summary = "Entra com e-mail e senha", description = "Em sucesso, define os cookies `HttpOnly` de sessão (access + refresh) e devolve o usuário autenticado no corpo; nunca devolve os tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO"),
            @ApiResponse(responseCode = "403", description = "EMAIL_NAO_VERIFICADO ou SENHA_NAO_DEFINIDA (conta que só entra pelo Google)"),
    })
    @PostMapping("/login")
    public ResponseEntity<UsuarioDTO> usuarioLogin(@Valid @RequestBody RequestLoginDTO requestLoginDTO,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        String email = EmailCanonicalizer.normalize(requestLoginDTO.getEmail());
        rateLimiter.checkLogin(request.getRemoteAddr(), EmailCanonicalizer.canonicalize(email));

        Usuario usuarioExistente = usuarioService.getUsuarioByEmail(email);
        if (usuarioExistente != null && !usuarioExistente.isSenhaDefinida()) {
            throw new BusinessRuleException(ErrorCode.SENHA_NAO_DEFINIDA);
        }

        String accessToken = jwtService.authenticate(email, requestLoginDTO.getSenha());
        Usuario usuario = usuarioService.getUsuarioByEmail(email);
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessRuleException(ErrorCode.EMAIL_NAO_VERIFICADO);
        }
        String refreshToken = refreshTokenService.issue(usuario);

        authCookies.addAccess(request, response, accessToken);
        authCookies.addRefresh(request, response, refreshToken);

        return ResponseEntity.ok(paraDTO(usuario));
    }

    @Operation(summary = "Entra ou cria conta com o Google", description = "Recebe o ID token do Google Identity Services, verifica a assinatura/emissor/audiência e cria a conta automaticamente se não existir (ATIVA, sem verificação de e-mail). Define os mesmos cookies do login comum.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "GOOGLE_TOKEN_INVALIDO"),
            @ApiResponse(responseCode = "409", description = "LIMITE_CONTAS_ATINGIDO"),
            @ApiResponse(responseCode = "503", description = "GOOGLE_AUTH_INDISPONIVEL (sem client id configurado)"),
    })
    @PostMapping("/entrar-google")
    public ResponseEntity<UsuarioDTO> entrarComGoogle(@Valid @RequestBody RequestGoogleLoginDTO requisicao,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        rateLimiter.checkSignup(request.getRemoteAddr());
        Usuario usuario = googleLoginService.entrarOuCriar(requisicao.credential());
        UserDetails userDetails = usuarioService.loadUserByUsername(usuario.getEmail());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.issue(usuario);

        authCookies.addAccess(request, response, accessToken);
        authCookies.addRefresh(request, response, refreshToken);

        return ResponseEntity.ok(paraDTO(usuario));
    }

    @Operation(summary = "Renova a sessão", description = "Troca o refresh token do cookie por um novo (rotação a cada uso); reuso de um token já revogado revoga todos os tokens do usuário por segurança.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "TOKEN_ATUALIZACAO_INVALIDO"),
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookies.readRefresh(request)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO));

        RefreshTokenService.RenewedToken renovado;
        try {
            renovado = refreshTokenService.renew(refreshToken);
        } catch (BusinessRuleException e) {
            authCookies.clear(request, response);
            throw e;
        }

        UserDetails userDetails = usuarioService.loadUserByUsername(renovado.email());
        authCookies.addAccess(request, response, jwtService.generateToken(userDetails));
        authCookies.addRefresh(request, response, renovado.newToken());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sai da conta", description = "Revoga o refresh token do cookie e limpa os cookies de sessão; idempotente.")
    @PostMapping("/logout")
    public ResponseEntity<ResponseMessageDTO> logout(HttpServletRequest request, HttpServletResponse response) {
        authCookies.readRefresh(request).ifPresent(refreshTokenService::revoke);
        authCookies.clear(request, response);
        return ResponseEntity.ok(new ResponseMessageDTO("Logout realizado com sucesso"));
    }

    @Operation(summary = "Devolve o usuário autenticado", description = "Usado pelo front para saber se há sessão válida (o cookie HttpOnly não pode ser lido por JavaScript) e para obter o cookie XSRF-TOKEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO"),
    })
    @GetMapping("/eu")
    public ResponseEntity<UsuarioDTO> eu(Authentication authentication) {
        Usuario usuario = usuarioService.getUsuarioByEmail(authentication.getName());
        return ResponseEntity.ok(paraDTO(usuario));
    }

    private ResponseEntity<ResponseMessageDTO> respostaCadastro() {
        return ResponseEntity.accepted().body(new ResponseMessageDTO(
                "Se o e-mail puder ser usado, enviaremos um link de verificação. Verifique sua caixa de entrada."));
    }

    private UsuarioDTO paraDTO(Usuario usuario) {
        return new UsuarioDTO(usuario.getId().toString(), usuario.getNome(), usuario.getEmail(),
                usuario.isSenhaDefinida(), usuario.getTermosAceitosEm() != null);
    }
}
