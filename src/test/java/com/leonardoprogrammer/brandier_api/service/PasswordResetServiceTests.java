package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.PasswordResetToken;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.PasswordResetTokenRepository;
import com.leonardoprogrammer.brandier_api.security.OpaqueTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

    private static final Instant AGORA = Instant.parse("2026-01-01T12:00:00Z");
    private static final long VALIDADE_EM_MINUTOS = 30;
    private static final long INTERVALO_EM_SEGUNDOS = 60;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new PasswordResetService(tokenRepository, Clock.fixed(AGORA, ZoneOffset.UTC),
                VALIDADE_EM_MINUTOS, INTERVALO_EM_SEGUNDOS);
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("pessoa@exemplo.com");
    }

    @Test
    void issueGuardaApenasOHashEDevolveOTokenBrutoComValidadeDeTrintaMinutos() {
        when(tokenRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())).thenReturn(Optional.empty());

        Optional<String> bruto = service.issue(usuario);

        assertThat(bruto).isPresent();
        assertThat(bruto.get()).hasSize(43);
        ArgumentCaptor<PasswordResetToken> capturado = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(capturado.capture());
        PasswordResetToken salvo = capturado.getValue();
        assertThat(salvo.getTokenHash()).isEqualTo(OpaqueTokens.hash(bruto.get())).isNotEqualTo(bruto.get());
        assertThat(salvo.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(VALIDADE_EM_MINUTOS)));
        assertThat(salvo.getUsedAt()).isNull();
        verify(tokenRepository).deleteAllByUsuario(usuario.getId());
    }

    @Test
    void issueDentroDoIntervaloNaoGeraNovoToken() {
        PasswordResetToken recente = token("recente", AGORA.plusSeconds(1800), null, AGORA.minusSeconds(30));
        when(tokenRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())).thenReturn(Optional.of(recente));

        assertThat(service.issue(usuario)).isEmpty();

        verify(tokenRepository, never()).save(any());
        verify(tokenRepository, never()).deleteAllByUsuario(any());
    }

    @Test
    void issueDepoisDoIntervaloSubstituiOsTokensAnteriores() {
        PasswordResetToken antigo = token("antigo", AGORA.plusSeconds(1800), null, AGORA.minusSeconds(61));
        when(tokenRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())).thenReturn(Optional.of(antigo));

        assertThat(service.issue(usuario)).isPresent();

        verify(tokenRepository).deleteAllByUsuario(usuario.getId());
        verify(tokenRepository).save(any());
    }

    @Test
    void consumeComTokenValidoMarcaComoUsadoEDevolveOUsuario() {
        PasswordResetToken valido = token("valido", AGORA.plusSeconds(60), null, AGORA.minusSeconds(600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("valido"))).thenReturn(Optional.of(valido));
        when(tokenRepository.markUsedIfUnused(valido.getId(), AGORA)).thenReturn(1);

        assertThat(service.consume("valido")).isSameAs(usuario);
    }

    @Test
    void consumeComTokenDesconhecidoLancaTokenInvalido() {
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("desconhecido"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("desconhecido")).satisfies(this::deveSerTokenInvalido);
    }

    @Test
    void consumeComTokenExpiradoLancaTokenInvalidoSemMarcarComoUsado() {
        PasswordResetToken expirado = token("expirado", AGORA.minusSeconds(1), null, AGORA.minusSeconds(3600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("expirado"))).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.consume("expirado")).satisfies(this::deveSerTokenInvalido);

        verify(tokenRepository, never()).markUsedIfUnused(any(), any());
    }

    @Test
    void consumeComTokenJaUsadoLancaTokenInvalido() {
        PasswordResetToken usado = token("usado", AGORA.plusSeconds(60), AGORA.minusSeconds(5), AGORA.minusSeconds(600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("usado"))).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> service.consume("usado")).satisfies(this::deveSerTokenInvalido);

        verify(tokenRepository, never()).markUsedIfUnused(any(), any());
    }

    @Test
    void consumeQuandoOutroPedidoJaUsouOTokenLancaTokenInvalido() {
        PasswordResetToken concorrente = token("concorrente", AGORA.plusSeconds(60), null, AGORA.minusSeconds(600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("concorrente"))).thenReturn(Optional.of(concorrente));
        when(tokenRepository.markUsedIfUnused(concorrente.getId(), AGORA)).thenReturn(0);

        assertThatThrownBy(() -> service.consume("concorrente")).satisfies(this::deveSerTokenInvalido);
    }

    private void deveSerTokenInvalido(Throwable erro) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                regra -> assertThat(regra.getErrorCode()).isEqualTo(ErrorCode.TOKEN_REDEFINICAO_INVALIDO));
    }

    private PasswordResetToken token(String bruto, Instant expiraEm, Instant usadoEm, Instant criadoEm) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUsuario(usuario);
        token.setTokenHash(OpaqueTokens.hash(bruto));
        token.setExpiresAt(expiraEm);
        token.setUsedAt(usadoEm);
        token.setCreatedAt(criadoEm);
        return token;
    }
}
