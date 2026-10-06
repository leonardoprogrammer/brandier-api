package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.event.PasswordResetEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedefinicaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public RedefinicaoSenhaService(UsuarioRepository usuarioRepository,
                                   PasswordResetService passwordResetService,
                                   RefreshTokenService refreshTokenService,
                                   PasswordEncoder passwordEncoder,
                                   ApplicationEventPublisher eventPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void solicitar(String email) {
        usuarioRepository.findByEmailCanonico(EmailCanonicalizer.canonicalize(email))
                .filter(usuario -> usuario.getStatus() == StatusUsuario.ATIVO)
                .ifPresent(usuario -> passwordResetService.issue(usuario)
                        .ifPresent(token -> eventPublisher.publishEvent(
                                new PasswordResetEmailRequested(usuario.getEmail(), usuario.getNome(), token))));
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void redefinir(String token, String novaSenha) {
        Usuario usuario = passwordResetService.consume(token);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        refreshTokenService.revokeAll(usuario.getId());
    }
}
