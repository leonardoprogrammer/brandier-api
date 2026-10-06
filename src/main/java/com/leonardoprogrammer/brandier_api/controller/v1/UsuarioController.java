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
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.service.CadastroService;
import com.leonardoprogrammer.brandier_api.service.RefreshTokenService;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
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

    public UsuarioController(JwtService jwtService,
                             UsuarioService usuarioService,
                             RefreshTokenService refreshTokenService,
                             AuthCookies authCookies,
                             CadastroService cadastroService,
                             AuthRateLimiter rateLimiter) {
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
        this.cadastroService = cadastroService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<ResponseMessageDTO> cadastro(@Valid @RequestBody RequestCadastroDTO requestCadastroDTO,
                                                       HttpServletRequest request) {
        rateLimiter.checkSignup(request.getRemoteAddr());
        try {
            cadastroService.cadastrar(requestCadastroDTO.getNome(), requestCadastroDTO.getEmail(), requestCadastroDTO.getSenha());
        } catch (DataIntegrityViolationException e) {
            return respostaCadastro();
        }
        return respostaCadastro();
    }

    @PostMapping("/verificar-email")
    public ResponseEntity<ResponseMessageDTO> verificarEmail(@Valid @RequestBody RequestVerificarEmailDTO requestVerificarEmailDTO,
                                                             HttpServletRequest request) {
        rateLimiter.checkToken(OPERATION_VERIFY_EMAIL, request.getRemoteAddr());
        cadastroService.verificarEmail(requestVerificarEmailDTO.getToken());
        return ResponseEntity.ok(new ResponseMessageDTO("E-mail verificado com sucesso. Você já pode entrar."));
    }

    @PostMapping("/reenviar-verificacao")
    public ResponseEntity<ResponseMessageDTO> reenviarVerificacao(@Valid @RequestBody RequestReenviarVerificacaoDTO requestReenviarVerificacaoDTO,
                                                                  HttpServletRequest request) {
        rateLimiter.checkRecovery(OPERATION_RESEND_VERIFICATION, request.getRemoteAddr(),
                EmailCanonicalizer.canonicalize(requestReenviarVerificacaoDTO.getEmail()));
        cadastroService.reenviarVerificacao(requestReenviarVerificacaoDTO.getEmail());
        return respostaCadastro();
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioDTO> usuarioLogin(@Valid @RequestBody RequestLoginDTO requestLoginDTO,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        String email = EmailCanonicalizer.normalize(requestLoginDTO.getEmail());
        rateLimiter.checkLogin(request.getRemoteAddr(), EmailCanonicalizer.canonicalize(email));
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

    @PostMapping("/logout")
    public ResponseEntity<ResponseMessageDTO> logout(HttpServletRequest request, HttpServletResponse response) {
        authCookies.readRefresh(request).ifPresent(refreshTokenService::revoke);
        authCookies.clear(request, response);
        return ResponseEntity.ok(new ResponseMessageDTO("Logout realizado com sucesso"));
    }

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
        return new UsuarioDTO(usuario.getId().toString(), usuario.getNome(), usuario.getEmail());
    }
}
