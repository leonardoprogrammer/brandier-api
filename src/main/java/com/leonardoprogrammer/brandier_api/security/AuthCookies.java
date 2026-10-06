package com.leonardoprogrammer.brandier_api.security;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "brandier_access";
    public static final String REFRESH_COOKIE = "brandier_refresh";

    private static final String ACCESS_SAME_SITE = "Lax";
    private static final String REFRESH_SAME_SITE = "Strict";

    private final boolean secure;
    private final Duration accessLifetime;
    private final Duration refreshLifetime;

    public AuthCookies(@Value("${brandier.cookie.secure}") boolean secure,
                       @Value("${jwt.expiration}") long accessLifetimeMillis,
                       @Value("${jwt.refresh-expiration}") long refreshLifetimeMillis) {
        this.secure = secure;
        this.accessLifetime = Duration.ofMillis(accessLifetimeMillis);
        this.refreshLifetime = Duration.ofMillis(refreshLifetimeMillis);
    }

    public boolean isSecure() {
        return secure;
    }

    public void addAccess(HttpServletRequest request, HttpServletResponse response, String token) {
        add(response, build(ACCESS_COOKIE, token, accessPath(request), ACCESS_SAME_SITE, accessLifetime));
    }

    public void addRefresh(HttpServletRequest request, HttpServletResponse response, String token) {
        add(response, build(REFRESH_COOKIE, token, refreshPath(request), REFRESH_SAME_SITE, refreshLifetime));
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        add(response, build(ACCESS_COOKIE, "", accessPath(request), ACCESS_SAME_SITE, Duration.ZERO));
        add(response, build(REFRESH_COOKIE, "", refreshPath(request), REFRESH_SAME_SITE, Duration.ZERO));
    }

    public Optional<String> readAccess(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String name, String value, String path, String sameSite, Duration lifetime) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(lifetime)
                .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String accessPath(HttpServletRequest request) {
        return request.getContextPath() + "/";
    }

    private String refreshPath(HttpServletRequest request) {
        return request.getContextPath() + ApiVersion.V1 + "/usuario";
    }
}
