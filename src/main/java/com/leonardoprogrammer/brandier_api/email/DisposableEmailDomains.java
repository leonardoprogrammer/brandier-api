package com.leonardoprogrammer.brandier_api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DisposableEmailDomains {

    private static final Logger log = LoggerFactory.getLogger(DisposableEmailDomains.class);
    private static final String DEFAULT_RESOURCE = "disposable-email-domains.txt";

    private final boolean enabled;
    private final Set<String> domains;

    @Autowired
    public DisposableEmailDomains(@Value("${brandier.email.block-disposable}") boolean enabled) {
        this(enabled, DEFAULT_RESOURCE);
    }

    DisposableEmailDomains(boolean enabled, String resourcePath) {
        this.enabled = enabled;
        this.domains = enabled ? load(resourcePath) : Set.of();
    }

    public boolean isDisposable(String email) {
        if (!enabled || domains.isEmpty() || email == null) {
            return false;
        }

        int separator = email.lastIndexOf('@');
        String domain = (separator >= 0 ? email.substring(separator + 1) : email).trim().toLowerCase(Locale.ROOT);

        while (domain.contains(".")) {
            if (domains.contains(domain)) {
                return true;
            }
            domain = domain.substring(domain.indexOf('.') + 1);
        }
        return false;
    }

    private static Set<String> load(String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.error("Lista de e-mails descartáveis não encontrada ({}). O bloqueio de e-mails descartáveis está DESATIVADO.", resourcePath);
            return Set.of();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            Set<String> loaded = reader.lines()
                    .map(line -> line.trim().toLowerCase(Locale.ROOT))
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
            if (loaded.isEmpty()) {
                log.error("Lista de e-mails descartáveis vazia ({}). O bloqueio de e-mails descartáveis está DESATIVADO.", resourcePath);
            } else {
                log.info("Lista de e-mails descartáveis carregada: {} domínios.", loaded.size());
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao ler a lista de e-mails descartáveis ({}): {}. O bloqueio de e-mails descartáveis está DESATIVADO.",
                    resourcePath, e.getClass().getSimpleName());
            return Set.of();
        }
    }
}
