package com.leonardoprogrammer.brandier_api.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.leonardoprogrammer.brandier_api.config.GoogleAuthProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleTokenServiceTests {

    @Mock
    private GoogleIdTokenVerifier verifier;

    private GoogleTokenService service;

    @BeforeEach
    void preparar() {
        service = new GoogleTokenService(new GoogleAuthProperties("client-id-de-teste"), verifier);
    }

    @Test
    void comTokenValidoDevolveOPerfilDoGoogle() throws Exception {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("pessoa@exemplo.com");
        payload.setEmailVerified(true);
        payload.set("name", "Pessoa Teste");
        when(idToken.getPayload()).thenReturn(payload);
        when(verifier.verify("credencial-valida")).thenReturn(idToken);

        GoogleTokenService.GooglePerfil perfil = service.verificar("credencial-valida");

        assertThat(perfil.email()).isEqualTo("pessoa@exemplo.com");
        assertThat(perfil.nome()).isEqualTo("Pessoa Teste");
        assertThat(perfil.emailVerificado()).isTrue();
    }

    @Test
    void comTokenQueNaoVerificaLancaGoogleTokenInvalido() throws Exception {
        when(verifier.verify("credencial-ruim")).thenReturn(null);

        assertThatThrownBy(() -> service.verificar("credencial-ruim"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALIDO));
    }

    @Test
    void comErroNaVerificacaoLancaGoogleTokenInvalido() throws Exception {
        when(verifier.verify("credencial-quebrada")).thenThrow(new RuntimeException("falha de rede"));

        assertThatThrownBy(() -> service.verificar("credencial-quebrada"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALIDO));
    }

    @Test
    void semClientIdConfiguradoLancaGoogleAuthIndisponivel() {
        service = new GoogleTokenService(new GoogleAuthProperties(""), verifier);

        assertThatThrownBy(() -> service.verificar("qualquer-token"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_INDISPONIVEL));
    }
}
