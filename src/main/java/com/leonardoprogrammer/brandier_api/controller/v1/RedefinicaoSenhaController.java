package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.model.dto.RequestEsqueciSenhaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestRedefinirSenhaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ResponseMessageDTO;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.service.RedefinicaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.V1 + "/usuario")
public class RedefinicaoSenhaController {

    private static final String OPERATION_REQUEST = "password-reset-request";
    private static final String OPERATION_CONFIRM = "password-reset-confirm";

    private final RedefinicaoSenhaService redefinicaoSenhaService;
    private final AuthRateLimiter rateLimiter;
    private final AuthCookies authCookies;

    public RedefinicaoSenhaController(RedefinicaoSenhaService redefinicaoSenhaService,
                                      AuthRateLimiter rateLimiter,
                                      AuthCookies authCookies) {
        this.redefinicaoSenhaService = redefinicaoSenhaService;
        this.rateLimiter = rateLimiter;
        this.authCookies = authCookies;
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<ResponseMessageDTO> esqueciSenha(@Valid @RequestBody RequestEsqueciSenhaDTO requestEsqueciSenhaDTO,
                                                           HttpServletRequest request) {
        rateLimiter.checkRecovery(OPERATION_REQUEST, request.getRemoteAddr(),
                EmailCanonicalizer.canonicalize(requestEsqueciSenhaDTO.getEmail()));

        redefinicaoSenhaService.solicitar(requestEsqueciSenhaDTO.getEmail());

        return ResponseEntity.accepted().body(new ResponseMessageDTO(
                "Se o e-mail estiver cadastrado, enviaremos um link para redefinir a senha. Verifique sua caixa de entrada."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<ResponseMessageDTO> redefinirSenha(@Valid @RequestBody RequestRedefinirSenhaDTO requestRedefinirSenhaDTO,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        rateLimiter.checkToken(OPERATION_CONFIRM, request.getRemoteAddr());

        redefinicaoSenhaService.redefinir(requestRedefinirSenhaDTO.getToken(), requestRedefinirSenhaDTO.getSenha());
        authCookies.clear(request, response);

        return ResponseEntity.ok(new ResponseMessageDTO("Senha redefinida com sucesso. Entre com a nova senha."));
    }
}
