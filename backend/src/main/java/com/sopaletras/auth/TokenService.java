package com.sopaletras.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;
import com.sopaletras.common.Tokens;
import com.sopaletras.config.AppProperties;
import com.sopaletras.user.User;

/**
 * Access token: JWT corto (15 min) con id de usuario y rol.
 * Refresh token: valor aleatorio largo (30 días), guardado solo como hash y <b>rotado en cada uso</b>.
 * Si un refresh ya usado vuelve a aparecer, se revoca toda la familia (señal de robo).
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties.Jwt cfg;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.cfg = props.jwt();
        this.clock = clock;
    }

    public record Issued(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {
    }

    /** Inicia una sesión nueva (familia nueva). */
    @Transactional
    public Issued issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Rota el refresh token. noRollbackFor: si se detecta reutilización, la revocación de la
     * familia debe quedar guardada aunque la petición responda con error.
     */
    @Transactional(noRollbackFor = ApiException.class, propagation = Propagation.REQUIRED)
    public RotateResult rotate(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(Tokens.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "La sesión no es válida; inicia sesión."));
        if (current.isRevoked() || current.getUsedAt() != null) {
            refreshTokens.revokeFamily(current.getFamily());
            throw ApiException.unauthorized("REFRESH_REUSED", "La sesión se cerró por seguridad; inicia sesión de nuevo.");
        }
        if (now.isAfter(current.getExpiresAt())) {
            throw ApiException.unauthorized("REFRESH_EXPIRED", "La sesión expiró; inicia sesión.");
        }
        current.markUsed(now);
        return new RotateResult(current.getUserId(), current.getFamily());
    }

    public record RotateResult(UUID userId, UUID family) {
    }

    @Transactional
    public Issued issue(User user, UUID family) {
        Instant now = clock.instant();
        Instant accessExp = now.plus(cfg.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(cfg.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExp)
                .claim("role", user.getRole().name())
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String refresh = Tokens.random();
        Instant refreshExp = now.plus(cfg.refreshTtl());
        refreshTokens.save(new RefreshToken(user.getId(), Tokens.sha256(refresh), family, refreshExp, now));
        return new Issued(access, accessExp, refresh, refreshExp);
    }

    @Transactional
    public void revoke(String refreshToken) {
        refreshTokens.findByTokenHash(Tokens.sha256(refreshToken)).ifPresent(t -> refreshTokens.revokeFamily(t.getFamily()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void purgeExpired() {
        refreshTokens.deleteExpired(clock.instant().minus(java.time.Duration.ofDays(7)));
    }
}
