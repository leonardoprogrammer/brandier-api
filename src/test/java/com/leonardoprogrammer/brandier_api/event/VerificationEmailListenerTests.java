package com.leonardoprogrammer.brandier_api.event;

import com.leonardoprogrammer.brandier_api.service.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationEmailListenerTests {

    @Mock
    private MailService mailService;

    @Test
    void enviaOEmailComOLinkDeVerificacaoEOPrazo() {
        VerificationEmailListener listener = new VerificationEmailListener(mailService, "https://exemplo.com", 24);

        listener.onVerificationEmailRequested(new VerificationEmailRequested("pessoa@exemplo.com", "Pessoa", "abc-123_XYZ"));

        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("pessoa@exemplo.com"), assunto.capture(), corpo.capture());
        assertThat(assunto.getValue()).isEqualTo("Confirme seu e-mail no Brandier");
        assertThat(corpo.getValue())
                .contains("Olá, Pessoa!")
                .contains("https://exemplo.com/verificar-email?token=abc-123_XYZ")
                .contains("24 horas");
    }

    @Test
    void removeABarraFinalDaUrlBase() {
        VerificationEmailListener listener = new VerificationEmailListener(mailService, "https://exemplo.com/", 24);

        listener.onVerificationEmailRequested(new VerificationEmailRequested("pessoa@exemplo.com", "Pessoa", "tok"));

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), corpo.capture());
        assertThat(corpo.getValue()).contains("https://exemplo.com/verificar-email?token=tok");
    }

    @Test
    void falhaNoEnvioNaoPropagaExcecao() {
        VerificationEmailListener listener = new VerificationEmailListener(mailService, "https://exemplo.com", 24);
        doThrow(new IllegalStateException("smtp fora do ar")).when(mailService).send(anyString(), anyString(), anyString());

        assertThatCode(() -> listener.onVerificationEmailRequested(
                new VerificationEmailRequested("pessoa@exemplo.com", "Pessoa", "tok"))).doesNotThrowAnyException();
    }
}
