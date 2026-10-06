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
class ConviteEmailListenerTests {

    @Mock
    private MailService mailService;

    @Test
    void enviaOEmailComOLinkDoConviteOGestorAMarcaEOPrazo() {
        ConviteEmailListener listener = new ConviteEmailListener(mailService, "https://exemplo.com/", 7);

        listener.onConviteEmailRequested(new ConviteEmailRequested("pessoa@exemplo.com", "Gestor Silva", "Marca Alfa", "abc-123_XYZ"));

        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("pessoa@exemplo.com"), assunto.capture(), corpo.capture());
        assertThat(assunto.getValue()).isEqualTo("Convite para ser promotor no Brandier");
        assertThat(corpo.getValue())
                .contains("Gestor Silva")
                .contains("Marca Alfa")
                .contains("https://exemplo.com/convite?token=abc-123_XYZ")
                .contains("7 dias")
                .contains("crie a conta com este mesmo e-mail");
    }

    @Test
    void falhaNoEnvioNaoPropagaExcecao() {
        ConviteEmailListener listener = new ConviteEmailListener(mailService, "https://exemplo.com", 7);
        doThrow(new IllegalStateException("smtp fora do ar")).when(mailService).send(anyString(), anyString(), anyString());

        assertThatCode(() -> listener.onConviteEmailRequested(
                new ConviteEmailRequested("pessoa@exemplo.com", "Gestor", "Marca", "tok"))).doesNotThrowAnyException();
    }
}
