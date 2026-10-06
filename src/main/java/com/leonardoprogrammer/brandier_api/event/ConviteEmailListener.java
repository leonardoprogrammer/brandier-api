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
public class ConviteEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ConviteEmailListener.class);
    private static final String SUBJECT = "Convite para ser promotor no Brandier";

    private final MailService mailService;
    private final String baseUrl;
    private final long expirationDays;

    public ConviteEmailListener(MailService mailService,
                                @Value("${brandier.base-url}") String baseUrl,
                                @Value("${brandier.invitation.expiration-days}") long expirationDays) {
        this.mailService = mailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.expirationDays = expirationDays;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConviteEmailRequested(ConviteEmailRequested event) {
        try {
            mailService.send(event.to(), SUBJECT, buildBody(event));
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de convite de promotor: {}", e.getClass().getSimpleName());
        }
    }

    private String buildBody(ConviteEmailRequested event) {
        String link = baseUrl + "/convite?token=" + event.token();
        return "Olá!\n\n"
                + event.gestorNome() + " convidou você para atuar como promotor da marca " + event.marcaNome()
                + " no Brandier.\n\n"
                + "Para ver e responder ao convite, acesse o link abaixo (válido por " + expirationDays + " dias). "
                + "Entre com a conta cadastrada neste e-mail ou, se ainda não tiver uma, crie a conta com este mesmo e-mail:\n\n"
                + link + "\n\n"
                + "Se você não esperava este convite, ignore esta mensagem.\n\n"
                + "Brandier é um projeto de portfólio, sem fins comerciais.";
    }
}
