package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.model.entity.RefreshToken;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private static final Instant AGORA = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        Clock relogioFixo = Clock.fixed(AGORA, ZoneOffset.UTC);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, relogioFixo, VALIDADE.toMillis());

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("pessoa@exemplo.com");
    }

    @Test
    void emitirGuardaApenasOHashEDevolveOTokenBruto() {
        String tokenBruto = refreshTokenService.issue(usuario);

        ArgumentCaptor<RefreshToken> capturado = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(capturado.capture());
        RefreshToken salvo = capturado.getValue();

        assertThat(tokenBruto).hasSize(43);
        assertThat(salvo.getTokenHash()).isEqualTo(hash(tokenBruto)).isNotEqualTo(tokenBruto);
        assertThat(salvo.getUsuario()).isSameAs(usuario);
        assertThat(salvo.getCreatedAt()).isEqualTo(AGORA);
        assertThat(salvo.getExpiresAt()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(salvo.getRevokedAt()).isNull();
    }

    @Test
    void emitirGeraTokensDiferentesACadaChamada() {
        String primeiro = refreshTokenService.issue(usuario);
        String segundo = refreshTokenService.issue(usuario);

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void renovarComTokenValidoRevogaOAtualEEmiteUmNovo() {
        RefreshToken atual = tokenExistente("token-antigo", AGORA.plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(hash("token-antigo"))).thenReturn(Optional.of(atual));
        when(refreshTokenRepository.revokeIfActive(atual.getId(), AGORA)).thenReturn(1);

        RefreshTokenService.RenewedToken renovado = refreshTokenService.renew("token-antigo");

        assertThat(renovado.email()).isEqualTo("pessoa@exemplo.com");
        assertThat(renovado.newToken()).isNotBlank().isNotEqualTo("token-antigo");
        verify(refreshTokenRepository).revokeIfActive(atual.getId(), AGORA);
        ArgumentCaptor<RefreshToken> capturado = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(capturado.capture());
        assertThat(capturado.getValue().getTokenHash()).isEqualTo(hash(renovado.newToken()));
    }

    @Test
    void renovarComTokenDesconhecidoLancaTokenInvalido() {
        when(refreshTokenRepository.findByTokenHash(hash("desconhecido"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.renew("desconhecido"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO));

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeIfActive(any(), any());
        verify(refreshTokenRepository, never()).revokeAllByUsuario(any(), any());
    }

    @Test
    void renovarComTokenJaRevogadoRevogaTodosOsTokensDoUsuario() {
        RefreshToken revogado = tokenExistente("token-reusado", AGORA.plusSeconds(60), AGORA.minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(hash("token-reusado"))).thenReturn(Optional.of(revogado));

        assertThatThrownBy(() -> refreshTokenService.renew("token-reusado"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO));

        verify(refreshTokenRepository).revokeAllByUsuario(usuario.getId(), AGORA);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void renovarComTokenExpiradoLancaTokenInvalidoSemRevogarOsDemais() {
        RefreshToken expirado = tokenExistente("token-expirado", AGORA.minusSeconds(1), null);
        when(refreshTokenRepository.findByTokenHash(hash("token-expirado"))).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> refreshTokenService.renew("token-expirado"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO));

        verify(refreshTokenRepository, never()).revokeIfActive(any(), any());
        verify(refreshTokenRepository, never()).revokeAllByUsuario(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void renovarQuandoOutroPedidoJaRevogouOTokenRevogaTodosOsTokensDoUsuario() {
        RefreshToken atual = tokenExistente("token-concorrente", AGORA.plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(hash("token-concorrente"))).thenReturn(Optional.of(atual));
        when(refreshTokenRepository.revokeIfActive(atual.getId(), AGORA)).thenReturn(0);

        assertThatThrownBy(() -> refreshTokenService.renew("token-concorrente"))
                .isInstanceOf(BusinessRuleException.class);

        verify(refreshTokenRepository).revokeAllByUsuario(usuario.getId(), AGORA);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revogarComTokenConhecidoRevogaApenasEsseToken() {
        RefreshToken atual = tokenExistente("token-logout", AGORA.plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(hash("token-logout"))).thenReturn(Optional.of(atual));

        refreshTokenService.revoke("token-logout");

        verify(refreshTokenRepository).revokeIfActive(atual.getId(), AGORA);
        verify(refreshTokenRepository, never()).revokeAllByUsuario(any(), any());
    }

    @Test
    void revogarComTokenDesconhecidoNaoFazNadaENaoLancaErro() {
        when(refreshTokenRepository.findByTokenHash(hash("desconhecido"))).thenReturn(Optional.empty());

        refreshTokenService.revoke("desconhecido");

        verify(refreshTokenRepository, never()).revokeIfActive(any(), any());
    }

    private RefreshToken tokenExistente(String tokenBruto, Instant expiraEm, Instant revogadoEm) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUsuario(usuario);
        token.setTokenHash(hash(tokenBruto));
        token.setCreatedAt(AGORA.minusSeconds(3600));
        token.setExpiresAt(expiraEm);
        token.setRevokedAt(revogadoEm);
        return token;
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
