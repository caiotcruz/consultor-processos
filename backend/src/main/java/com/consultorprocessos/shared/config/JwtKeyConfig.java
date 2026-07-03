// src/main/java/com/consultorprocessos/shared/config/JwtKeyConfig.java
package com.consultorprocessos.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@Slf4j
public class JwtKeyConfig {

    @Bean
    @Profile({"dev", "test"})
    public KeyPair devTestKeyPair() throws NoSuchAlgorithmException {
        log.warn("[JWT] Usando chaves RSA geradas em memória (perfil DEV/TEST). " +
                 "Tokens são invalidados a cada restart. NUNCA usar em produção.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        return generator.generateKeyPair();
    }

    @Bean
    @Profile("prod")
    public KeyPair prodKeyPair(
            @Value("${app.jwt.private-key}") String privateKeyB64,
            @Value("${app.jwt.public-key}")  String publicKeyB64) throws Exception {

        if (!StringUtils.hasText(privateKeyB64) || !StringUtils.hasText(publicKeyB64)) {
            throw new IllegalStateException(
                "JWT_PRIVATE_KEY e JWT_PUBLIC_KEY são obrigatórias no perfil prod. " +
                "Consulte application-prod.yml para instruções de geração.");
        }

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyB64);
        byte[] publicKeyBytes  = Base64.getDecoder().decode(publicKeyB64);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        PrivateKey privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(privateKeyBytes));
        PublicKey publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(publicKeyBytes));

        log.info("[JWT] Chaves RSA carregadas das variáveis de ambiente.");
        return new KeyPair(publicKey, privateKey);
    }
}