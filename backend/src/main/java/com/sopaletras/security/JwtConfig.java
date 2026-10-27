package com.sopaletras.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sopaletras.common.Tokens;
import com.sopaletras.config.AppProperties;

/** Firma y verificación de los access tokens propios (HS256). */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final SecretKey key;
    private final String issuer;

    public JwtConfig(AppProperties props, Environment env) {
        String secret = props.jwt().secret();
        if ((secret == null || secret.isBlank()) && env.matchesProfiles("prod")) {
            throw new IllegalStateException("APP_JWT_SECRET es obligatorio con el perfil prod.");
        }
        if (secret == null || secret.isBlank()) {
            log.warn("APP_JWT_SECRET no está definido: se usa una clave temporal y las sesiones "
                    + "se invalidarán al reiniciar. Defínelo en cualquier ambiente compartido.");
            secret = Tokens.random() + Tokens.random();
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET debe tener al menos 32 caracteres.");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.issuer = props.jwt().issuer();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer)));
        return decoder;
    }
}
