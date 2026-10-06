package com.leonardoprogrammer.brandier_api.security;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTests {

    @Mock
    private UsuarioService usuarioService;

    @Test
    void devolveOUsuarioDaAutenticacao() {
        Usuario usuario = new Usuario();
        when(usuarioService.getUsuarioByEmail("pessoa@exemplo.com")).thenReturn(usuario);
        CurrentUserResolver resolver = new CurrentUserResolver(usuarioService);

        Usuario resolvido = resolver.resolve(
                UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of()));

        assertThat(resolvido).isSameAs(usuario);
    }

    @Test
    void semAutenticacaoLancaNaoAutenticado() {
        CurrentUserResolver resolver = new CurrentUserResolver(usuarioService);

        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.NAO_AUTENTICADO));
    }

    @Test
    void usuarioInexistenteLancaNaoAutenticado() {
        when(usuarioService.getUsuarioByEmail("fantasma@exemplo.com")).thenReturn(null);
        CurrentUserResolver resolver = new CurrentUserResolver(usuarioService);

        assertThatThrownBy(() -> resolver.resolve(
                UsernamePasswordAuthenticationToken.authenticated("fantasma@exemplo.com", null, List.of())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.NAO_AUTENTICADO));
    }
}
