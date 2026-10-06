package com.leonardoprogrammer.brandier_api.event;

import com.leonardoprogrammer.brandier_api.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);
    private static final String SUBJECT = "Redefinição de senha no Brandier";

    private final MailService mailService;
    private final String baseUrl;
    private final long tokenExpirationMinutes;

    public PasswordResetEmailListener(MailService mailService,
                                      @Value("${brandier.base-url}") String baseUrl,
                                      @Value("${brandier.password-reset.token-expiration-minutes}") long tokenExpirationMinutes) {
        this.mailService = mailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tokenExpirationMinutes = tokenExpirationMinutes;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetEmailRequested(PasswordResetEmailRequested event) {
        try {
            mailService.send(event.to(), SUBJECT, buildBody(event));
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de redefinição de senha: {}", e.getClass().getSimpleName());
        }
    }

    private String buildBody(PasswordResetEmailRequested event) {
        String link = baseUrl + "/redefinir-senha?token=" + event.token();
        return "Olá, " + event.nome() + "!\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta no Brandier. "
                + "Para escolher uma nova senha, acesse o link abaixo (válido por " + tokenExpirationMinutes + " minutos):\n\n"
                + link + "\n\n"
                + "Se você não fez esse pedido, ignore esta mensagem: sua senha atual continua valendo.\n\n"
                + "Brandier é um projeto de portfólio, sem fins comerciais.";
    }
}
