package com.leonardoprogrammer.brandier_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${brandier.mail.enabled}") boolean enabled,
                       @Value("${brandier.mail.from}") String from) {
        this.from = from;
        this.mailSender = enabled ? mailSenderProvider.getIfAvailable() : null;
        if (enabled && this.mailSender == null) {
            throw new IllegalStateException("brandier.mail.enabled=true exige a configuração spring.mail.host");
        }
    }

    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            log.info("E-mail não enviado (brandier.mail.enabled=false, apenas desenvolvimento)\nPara: {}\nAssunto: {}\n\n{}", to, subject, body);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            log.error("Falha de autenticação no servidor SMTP. Verifique spring.mail.username e spring.mail.password (no Gmail, use uma senha de app). Resposta do servidor: {}",
                    rootCauseMessage(e));
            throw e;
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
