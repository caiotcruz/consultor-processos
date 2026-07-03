package com.consultorprocessos.shared.config;

import com.consultorprocessos.auth.security.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    private final KeyPair keyPair;
    private final long    accessTokenExpiryMs;

    public JwtService(
            KeyPair keyPair,
            @Value("${app.jwt.access-token-expiry-minutes:15}") int expiryMinutes) {
        this.keyPair            = keyPair;
        this.accessTokenExpiryMs = (long) expiryMinutes * 60 * 1000;
    }

    public String generateAccessToken(UserDetailsImpl userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(userDetails.getUserId().toString())
                .claim("email", userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserIdUnchecked(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}