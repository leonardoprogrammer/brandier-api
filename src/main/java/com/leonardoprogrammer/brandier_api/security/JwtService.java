package com.leonardoprogrammer.brandier_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final String CLAIM_TIPO = "tipo";
    private static final String CLAIM_ROLES = "roles";
    private static final String TIPO_ACESSO = "ACESSO";

    private final AuthenticationManager authenticationManager;
    private final SecretKey signingKey;
    private final long expiration;

    public JwtService(AuthenticationManager authenticationManager,
                      @Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expiration) {
        this.authenticationManager = authenticationManager;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim(CLAIM_TIPO, TIPO_ACESSO)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusMillis(expiration)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String authenticate(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
            return generateToken((UserDetails) authentication.getPrincipal());
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public List<String> extractRoles(String token) {
        List<String> roles = parseClaims(token).get(CLAIM_ROLES, List.class);
        return roles == null ? List.of() : roles;
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        return TIPO_ACESSO.equals(claims.get(CLAIM_TIPO, String.class))
                && userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
