package com.sopaletras.auth;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.sopaletras.common.ApiException;
import com.sopaletras.config.AppProperties;

/**
 * Verificación con las claves públicas de Google (se descargan y se cachean):
 * firma RS256, emisor accounts.google.com, audiencia = uno de nuestros client IDs, y vigencia.
 */
@Component
public class GoogleJwtVerifier implements GoogleTokenVerifier {

    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final List<String> clientIds;
    private volatile NimbusJwtDecoder decoder;

    public GoogleJwtVerifier(AppProperties props) {
        this.clientIds = props.google().clientIds().stream().filter(s -> !s.isBlank()).toList();
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientIds.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "El inicio con Google no está configurado en el servidor.");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("INVALID_GOOGLE_TOKEN", "No se pudo verificar la cuenta de Google.");
        }
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
                    OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "iss inválido", null));
                    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                            && jwt.getAudience().stream().anyMatch(clientIds::contains)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "aud inválido", null));
                    built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(), issuer, audience));
                    decoder = built;
                }
                d = decoder;
            }
        }
        return d;
    }
}
