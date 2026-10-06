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
public class VerificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailListener.class);
    private static final String SUBJECT = "Confirme seu e-mail no Brandier";

    private final MailService mailService;
    private final String baseUrl;
    private final long tokenExpirationHours;

    public VerificationEmailListener(MailService mailService,
                                     @Value("${brandier.base-url}") String baseUrl,
                                     @Value("${brandier.verification.token-expiration-hours}") long tokenExpirationHours) {
        this.mailService = mailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tokenExpirationHours = tokenExpirationHours;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        try {
            mailService.send(event.to(), SUBJECT, buildBody(event));
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de verificação: {}", e.getClass().getSimpleName());
        }
    }

    private String buildBody(VerificationEmailRequested event) {
        String link = baseUrl + "/verificar-email?token=" + event.token();
        return "Olá, " + event.nome() + "!\n\n"
                + "Para ativar sua conta no Brandier, confirme seu e-mail acessando o link abaixo (válido por "
                + tokenExpirationHours + " horas):\n\n"
                + link + "\n\n"
                + "Se você não criou uma conta, ignore esta mensagem.\n\n"
                + "Brandier é um projeto de portfólio, sem fins comerciais.";
    }
}
