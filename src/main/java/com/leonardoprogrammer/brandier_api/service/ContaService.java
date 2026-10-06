package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaService {

    private final UsuarioRepository usuarioRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final PasswordEncoder passwordEncoder;

    public ContaService(UsuarioRepository usuarioRepository,
                        ConvitePromotorRepository conviteRepository,
                        PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.conviteRepository = conviteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void excluir(Usuario usuario, String senha) {
        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new BusinessRuleException(ErrorCode.SENHA_INCORRETA);
        }

        conviteRepository.deleteByEmailCanonico(usuario.getEmailCanonico());
        usuarioRepository.delete(usuario);
    }
}
