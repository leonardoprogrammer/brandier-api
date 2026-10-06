package com.leonardoprogrammer.brandier_api.email;

import java.util.Locale;
import java.util.Set;

public final class EmailCanonicalizer {

    private static final Set<String> GMAIL_DOMAINS = Set.of("gmail.com", "googlemail.com");
    private static final String GMAIL_CANONICAL_DOMAIN = "gmail.com";
    private static final Set<String> PLUS_ADDRESSING_DOMAINS = Set.of(
            "outlook.com", "hotmail.com", "live.com", "msn.com",
            "icloud.com", "me.com", "mac.com",
            "proton.me", "protonmail.com", "pm.me",
            "fastmail.com");

    private EmailCanonicalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String canonicalize(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            return null;
        }

        int separator = normalized.lastIndexOf('@');
        if (separator <= 0 || separator == normalized.length() - 1) {
            return normalized;
        }

        String local = normalized.substring(0, separator);
        String domain = normalized.substring(separator + 1);

        if (GMAIL_DOMAINS.contains(domain)) {
            local = stripPlusTag(local).replace(".", "");
            domain = GMAIL_CANONICAL_DOMAIN;
        } else if (PLUS_ADDRESSING_DOMAINS.contains(domain)) {
            local = stripPlusTag(local);
        }

        return local.isEmpty() ? normalized : local + "@" + domain;
    }

    private static String stripPlusTag(String local) {
        int plus = local.indexOf('+');
        return plus > 0 ? local.substring(0, plus) : local;
    }
}
