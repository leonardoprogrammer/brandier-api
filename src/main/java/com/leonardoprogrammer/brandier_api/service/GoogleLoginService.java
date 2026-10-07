package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import com.leonardoprogrammer.brandier_api.security.GoogleTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GoogleLoginService {

    private final UsuarioRepository usuarioRepository;
    private final GoogleTokenService googleTokenService;
    private final PasswordEncoder passwordEncoder;
    private final LimitsProperties limits;

    public GoogleLoginService(UsuarioRepository usuarioRepository,
                              GoogleTokenService googleTokenService,
                              PasswordEncoder passwordEncoder,
                              LimitsProperties limits) {
        this.usuarioRepository = usuarioRepository;
        this.googleTokenService = googleTokenService;
        this.passwordEncoder = passwordEncoder;
        this.limits = limits;
    }

    @Transactional
    public Usuario entrarOuCriar(String credential) {
        GoogleTokenService.GooglePerfil perfil = googleTokenService.verificar(credential);
        if (!perfil.emailVerificado()) {
            throw new BusinessRuleException(ErrorCode.GOOGLE_TOKEN_INVALIDO);
        }

        String emailNormalizado = EmailCanonicalizer.normalize(perfil.email());
        String emailCanonico = EmailCanonicalizer.canonicalize(emailNormalizado);

        return usuarioRepository.findByEmailCanonico(emailCanonico)
                .orElseGet(() -> criar(perfil.nome(), emailNormalizado, emailCanonico));
    }

    private Usuario criar(String nome, String email, String emailCanonico) {
        if (usuarioRepository.countByAtivoTrue() >= limits.maxAccounts()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_CONTAS_ATINGIDO);
        }

        Usuario novo = new Usuario();
        novo.setNome(nome == null || nome.isBlank() ? "Usuário Google" : nome.trim());
        novo.setEmail(email);
        novo.setEmailCanonico(emailCanonico);
        novo.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
        novo.setSenhaDefinida(false);
        novo.setStatus(StatusUsuario.ATIVO);
        return usuarioRepository.save(novo);
    }
}
