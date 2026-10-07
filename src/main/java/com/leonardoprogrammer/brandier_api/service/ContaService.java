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

import java.util.UUID;

@Service
public class ContaService {

    private final UsuarioRepository usuarioRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final PromotorRepository promotorRepository;
    private final MarcaService marcaService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public ContaService(UsuarioRepository usuarioRepository,
                        ConvitePromotorRepository conviteRepository,
                        PromotorRepository promotorRepository,
                        MarcaService marcaService,
                        RefreshTokenService refreshTokenService,
                        PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.conviteRepository = conviteRepository;
        this.promotorRepository = promotorRepository;
        this.marcaService = marcaService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void excluir(Usuario usuario, String senha) {
        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
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
}
