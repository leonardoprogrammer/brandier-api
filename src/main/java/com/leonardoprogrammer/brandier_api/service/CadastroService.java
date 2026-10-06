package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.event.VerificationEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CadastroService(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           EmailVerificationService emailVerificationService,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void cadastrar(String nome, String email, String senha) {
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        String senhaHash = passwordEncoder.encode(senha);
        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);

        if (existente.isEmpty()) {
            Usuario novo = new Usuario();
            novo.setNome(nome.trim());
            novo.setEmail(emailNormalizado);
            novo.setSenha(senhaHash);
            novo.setStatus(StatusUsuario.PENDENTE);
            novo.setTermosAceitosEm(Instant.now(clock));
            usuarioRepository.save(novo);
            enviarVerificacao(novo);
            return;
        }

        Usuario pendente = existente.get();
        if (pendente.getStatus() != StatusUsuario.PENDENTE) {
            return;
        }

        emailVerificationService.issue(pendente).ifPresent(token -> {
            pendente.setNome(nome.trim());
            pendente.setSenha(senhaHash);
            pendente.setTermosAceitosEm(Instant.now(clock));
            usuarioRepository.save(pendente);
            publicar(pendente, token);
        });
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void verificarEmail(String token) {
        Usuario usuario = emailVerificationService.consume(token);
        usuario.setStatus(StatusUsuario.ATIVO);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void reenviarVerificacao(String email) {
        usuarioRepository.findByEmail(UsuarioService.normalizarEmail(email))
                .filter(usuario -> usuario.getStatus() == StatusUsuario.PENDENTE)
                .ifPresent(this::enviarVerificacao);
    }

    private void enviarVerificacao(Usuario usuario) {
        emailVerificationService.issue(usuario).ifPresent(token -> publicar(usuario, token));
    }

    private void publicar(Usuario usuario, String token) {
        eventPublisher.publishEvent(new VerificationEmailRequested(usuario.getEmail(), usuario.getNome(), token));
    }
}
