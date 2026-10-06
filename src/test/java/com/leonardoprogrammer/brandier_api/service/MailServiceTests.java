package com.leonardoprogrammer.brandier_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTests {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void habilitadoEnviaAMensagemPeloSmtp() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        MailService mailService = new MailService(mailSenderProvider, true, "Brandier <nao-responder@exemplo.com>");

        mailService.send("pessoa@exemplo.com", "Assunto", "Corpo");

        ArgumentCaptor<SimpleMailMessage> capturada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(capturada.capture());
        SimpleMailMessage mensagem = capturada.getValue();
        assertThat(mensagem.getFrom()).isEqualTo("Brandier <nao-responder@exemplo.com>");
        assertThat(mensagem.getTo()).containsExactly("pessoa@exemplo.com");
        assertThat(mensagem.getSubject()).isEqualTo("Assunto");
        assertThat(mensagem.getText()).isEqualTo("Corpo");
    }

    @Test
    void desabilitadoNaoEnviaENaoLancaErro() {
        MailService mailService = new MailService(mailSenderProvider, false, "Brandier <nao-responder@exemplo.com>");

        mailService.send("pessoa@exemplo.com", "Assunto", "Corpo");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void habilitadoSemSmtpConfiguradoFalhaNaInicializacao() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> new MailService(mailSenderProvider, true, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }
}
