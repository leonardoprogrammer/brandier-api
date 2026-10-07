package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContaServiceTests {

    private static final Instant AGORA = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ConvitePromotorRepository conviteRepository;

    @Mock
    private PromotorRepository promotorRepository;

    @Mock
    private MarcaService marcaService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ContaService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new ContaService(usuarioRepository, conviteRepository, promotorRepository, marcaService,
                refreshTokenService, passwordEncoder, Clock.fixed(AGORA, ZoneOffset.UTC));
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("pessoa@exemplo.com");
        usuario.setEmailCanonico("pessoa@exemplo.com");
        usuario.setSenha("hash-da-senha");
    }

    @Test
    void comASenhaCertaAnonimizaOUsuarioELimpaOQueDependeDele() {
        when(passwordEncoder.matches("Senha1234", "hash-da-senha")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("hash-nova");

        service.excluir(usuario, "Senha1234");

        verify(conviteRepository).deleteByEmailCanonico("pessoa@exemplo.com");
        verify(promotorRepository).deleteByUsuarioId(usuario.getId());
        verify(marcaService).excluirTodasDoGestor(usuario.getId());
        verify(refreshTokenService).revokeAll(usuario.getId());
        verify(usuarioRepository, never()).delete(any());
        verify(usuarioRepository).save(usuario);

        assertThat(usuario.isAtivo()).isFalse();
        assertThat(usuario.getEmail()).isEqualTo(usuario.getEmailCanonico());
        assertThat(usuario.getEmail()).contains(usuario.getId().toString()).endsWith("@removido.brandier");
        assertThat(usuario.getSenha()).isEqualTo("hash-nova");
    }

    @Test
    void comASenhaErradaLancaSenhaIncorretaENaoAlteraNada() {
        when(passwordEncoder.matches("errada", "hash-da-senha")).thenReturn(false);

        assertThatThrownBy(() -> service.excluir(usuario, "errada"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.SENHA_INCORRETA));

        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getEmail()).isEqualTo("pessoa@exemplo.com");
        verify(usuarioRepository, never()).save(any());
        verify(conviteRepository, never()).deleteByEmailCanonico(any());
        verify(promotorRepository, never()).deleteByUsuarioId(any());
        verify(marcaService, never()).excluirTodasDoGestor(any());
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void comContaSemSenhaDefinidaExcluiSemExigirSenha() {
        usuario.setSenhaDefinida(false);
        when(passwordEncoder.encode(any())).thenReturn("hash-nova");

        service.excluir(usuario, null);

        verify(usuarioRepository).save(usuario);
        assertThat(usuario.isAtivo()).isFalse();
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void atualizarNomeAplicaONomeSemEspacosNasPontas() {
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario atualizado = service.atualizarNome(usuario, "  Novo Nome  ");

        assertThat(atualizado.getNome()).isEqualTo("Novo Nome");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void alterarSenhaComASenhaAtualCertaTrocaOHashERevogaOsRefreshTokens() {
        when(passwordEncoder.matches("SenhaAtual1", "hash-da-senha")).thenReturn(true);
        when(passwordEncoder.encode("SenhaNova12")).thenReturn("hash-nova-senha");

        service.alterarSenha(usuario, "SenhaAtual1", "SenhaNova12");

        assertThat(usuario.getSenha()).isEqualTo("hash-nova-senha");
        verify(usuarioRepository).save(usuario);
        verify(refreshTokenService).revokeAll(usuario.getId());
    }

    @Test
    void alterarSenhaComASenhaAtualErradaLancaSenhaIncorretaENaoAlteraNada() {
        when(passwordEncoder.matches("errada", "hash-da-senha")).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenha(usuario, "errada", "SenhaNova12"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.SENHA_INCORRETA));

        assertThat(usuario.getSenha()).isEqualTo("hash-da-senha");
        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void comContaSemSenhaDefinidaDefineANovaSenhaSemExigirASenhaAtual() {
        usuario.setSenhaDefinida(false);
        when(passwordEncoder.encode("SenhaNova12")).thenReturn("hash-nova-senha");

        service.alterarSenha(usuario, null, "SenhaNova12");

        assertThat(usuario.getSenha()).isEqualTo("hash-nova-senha");
        assertThat(usuario.isSenhaDefinida()).isTrue();
        verify(usuarioRepository).save(usuario);
        verify(refreshTokenService).revokeAll(usuario.getId());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void aceitarTermosQuandoAindaNaoAceitouMarcaAData() {
        Usuario atualizado = service.aceitarTermos(usuario);

        assertThat(atualizado.getTermosAceitosEm()).isEqualTo(AGORA);
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void aceitarTermosQuandoJaAceitouNaoAlteraNadaNemSalvaDeNovo() {
        Instant aceiteAnterior = Instant.parse("2025-06-01T10:00:00Z");
        usuario.setTermosAceitosEm(aceiteAnterior);

        service.aceitarTermos(usuario);

        assertThat(usuario.getTermosAceitosEm()).isEqualTo(aceiteAnterior);
        verify(usuarioRepository, never()).save(any());
    }
}
