package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContaServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ConvitePromotorRepository conviteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ContaService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new ContaService(usuarioRepository, conviteRepository, passwordEncoder);
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("pessoa@exemplo.com");
        usuario.setEmailCanonico("pessoa@exemplo.com");
        usuario.setSenha("hash-da-senha");
    }

    @Test
    void comASenhaCertaApagaOsConvitesRecebidosEDepoisOUsuario() {
        when(passwordEncoder.matches("Senha1234", "hash-da-senha")).thenReturn(true);

        service.excluir(usuario, "Senha1234");

        InOrder ordem = inOrder(conviteRepository, usuarioRepository);
        ordem.verify(conviteRepository).deleteByEmailCanonico("pessoa@exemplo.com");
        ordem.verify(usuarioRepository).delete(usuario);
    }

    @Test
    void comASenhaErradaLancaSenhaIncorretaENaoApagaNada() {
        when(passwordEncoder.matches("errada", "hash-da-senha")).thenReturn(false);

        assertThatThrownBy(() -> service.excluir(usuario, "errada"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.SENHA_INCORRETA));

        verify(usuarioRepository, never()).delete(any());
        verify(conviteRepository, never()).deleteByEmailCanonico(any());
    }
}
