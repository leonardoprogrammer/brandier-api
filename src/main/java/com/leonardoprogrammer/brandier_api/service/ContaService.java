package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ContaService {

    private final UsuarioRepository usuarioRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final PromotorRepository promotorRepository;
    private final MarcaService marcaService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ContaService(UsuarioRepository usuarioRepository,
                        ConvitePromotorRepository conviteRepository,
                        PromotorRepository promotorRepository,
                        MarcaService marcaService,
                        RefreshTokenService refreshTokenService,
                        PasswordEncoder passwordEncoder,
                        Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.conviteRepository = conviteRepository;
        this.promotorRepository = promotorRepository;
        this.marcaService = marcaService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void excluir(Usuario usuario, String senha) {
        if (usuario.isSenhaDefinida() && (senha == null || senha.isBlank() || !passwordEncoder.matches(senha, usuario.getSenha()))) {
            throw new BusinessRuleException(ErrorCode.SENHA_INCORRETA);
        }

        conviteRepository.deleteByEmailCanonico(usuario.getEmailCanonico());
        promotorRepository.deleteByUsuarioId(usuario.getId());
        marcaService.excluirTodasDoGestor(usuario.getId());
        refreshTokenService.revokeAll(usuario.getId());

        String emailAnonimo = "excluido-" + usuario.getId() + "@removido.brandier";
        usuario.setEmail(emailAnonimo);
        usuario.setEmailCanonico(emailAnonimo);
        usuario.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario atualizarNome(Usuario usuario, String nome) {
        usuario.setNome(nome.trim());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void alterarSenha(Usuario usuario, String senhaAtual, String novaSenha) {
        if (usuario.isSenhaDefinida()
                && (senhaAtual == null || senhaAtual.isBlank() || !passwordEncoder.matches(senhaAtual, usuario.getSenha()))) {
            throw new BusinessRuleException(ErrorCode.SENHA_INCORRETA);
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setSenhaDefinida(true);
        usuarioRepository.save(usuario);
        refreshTokenService.revokeAll(usuario.getId());
    }

    @Transactional
    public Usuario aceitarTermos(Usuario usuario) {
        if (usuario.getTermosAceitosEm() == null) {
            usuario.setTermosAceitosEm(Instant.now(clock));
            usuarioRepository.save(usuario);
        }
        return usuario;
    }
}
