package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.event.PasswordResetEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedefinicaoSenhaServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RedefinicaoSenhaService service;

    @BeforeEach
    void preparar() {
        service = new RedefinicaoSenhaService(usuarioRepository, passwordResetService, refreshTokenService,
                passwordEncoder, eventPublisher);
    }

    private Usuario usuario(StatusUsuario status) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Pessoa");
        usuario.setEmail("pe.ssoa+x@gmail.com");
        usuario.setSenha("hash-antigo");
        usuario.setStatus(status);
        return usuario;
    }

    @Test
    void solicitarParaContaAtivaPublicaOEventoComOEmailCadastrado() {
        Usuario ativo = usuario(StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(ativo));
        when(passwordResetService.issue(ativo)).thenReturn(Optional.of("token-bruto"));

        service.solicitar("  P.essoa+qq@GMAIL.com ");

        ArgumentCaptor<PasswordResetEmailRequested> evento = ArgumentCaptor.forClass(PasswordResetEmailRequested.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().to()).isEqualTo("pe.ssoa+x@gmail.com");
        assertThat(evento.getValue().nome()).isEqualTo("Pessoa");
        assertThat(evento.getValue().token()).isEqualTo("token-bruto");
    }

    @Test
    void solicitarParaContaPendenteNaoFazNada() {
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(usuario(StatusUsuario.PENDENTE)));

        service.solicitar("pessoa@exemplo.com");

        verify(passwordResetService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void solicitarParaEmailInexistenteNaoFazNadaESemErro() {
        when(usuarioRepository.findByEmailCanonico("ninguem@exemplo.com")).thenReturn(Optional.empty());

        service.solicitar("ninguem@exemplo.com");

        verify(passwordResetService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void solicitarDentroDoIntervaloNaoPublicaEvento() {
        Usuario ativo = usuario(StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(ativo));
        when(passwordResetService.issue(ativo)).thenReturn(Optional.empty());

        service.solicitar("pessoa@exemplo.com");

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void redefinirTrocaASenhaEmHashEEncerraTodasAsSessoes() {
        Usuario ativo = usuario(StatusUsuario.ATIVO);
        when(passwordResetService.consume("token-valido")).thenReturn(ativo);
        when(passwordEncoder.encode("NovaSenha123")).thenReturn("hash-novo");

        service.redefinir("token-valido", "NovaSenha123");

        assertThat(ativo.getSenha()).isEqualTo("hash-novo");
        InOrder ordem = inOrder(usuarioRepository, refreshTokenService);
        ordem.verify(usuarioRepository).save(ativo);
        ordem.verify(refreshTokenService).revokeAll(ativo.getId());
    }

    @Test
    void redefinirComTokenInvalidoPropagaOErroENaoAlteraNada() {
        when(passwordResetService.consume("ruim"))
                .thenThrow(new BusinessRuleException(ErrorCode.TOKEN_REDEFINICAO_INVALIDO));

        assertThatThrownBy(() -> service.redefinir("ruim", "NovaSenha123"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.TOKEN_REDEFINICAO_INVALIDO));

        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
