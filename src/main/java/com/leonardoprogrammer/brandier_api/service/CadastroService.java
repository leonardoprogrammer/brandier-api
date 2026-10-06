package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.email.DisposableEmailDomains;
import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.event.VerificationEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
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
    private final DisposableEmailDomains disposableEmailDomains;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final LimitsProperties limits;

    public CadastroService(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           EmailVerificationService emailVerificationService,
                           DisposableEmailDomains disposableEmailDomains,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock,
                           LimitsProperties limits) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.disposableEmailDomains = disposableEmailDomains;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.limits = limits;
    }

    @Transactional
    public void cadastrar(String nome, String email, String senha) {
        if (usuarioRepository.count() >= limits.maxAccounts()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_CONTAS_ATINGIDO);
        }

        String emailNormalizado = EmailCanonicalizer.normalize(email);
        if (disposableEmailDomains.isDisposable(emailNormalizado)) {
            throw new BusinessRuleException(ErrorCode.EMAIL_DESCARTAVEL);
        }

        String emailCanonico = EmailCanonicalizer.canonicalize(emailNormalizado);
        String senhaHash = passwordEncoder.encode(senha);
        Optional<Usuario> existente = usuarioRepository.findByEmailCanonico(emailCanonico);

        if (existente.isEmpty()) {
            Usuario novo = new Usuario();
            novo.setNome(nome.trim());
            novo.setEmail(emailNormalizado);
            novo.setEmailCanonico(emailCanonico);
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
        usuarioRepository.findByEmailCanonico(EmailCanonicalizer.canonicalize(email))
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
