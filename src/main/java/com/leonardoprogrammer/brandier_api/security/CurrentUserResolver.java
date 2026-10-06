package com.leonardoprogrammer.brandier_api.security;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final UsuarioService usuarioService;

    public CurrentUserResolver(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public Usuario resolve(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessRuleException(ErrorCode.NAO_AUTENTICADO);
        }
        Usuario usuario = usuarioService.getUsuarioByEmail(authentication.getName());
        if (usuario == null) {
            throw new BusinessRuleException(ErrorCode.NAO_AUTENTICADO);
        }
        return usuario;
    }
}
