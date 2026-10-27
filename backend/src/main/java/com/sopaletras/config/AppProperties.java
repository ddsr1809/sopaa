package com.sopaletras.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia de la aplicación (prefijo {@code app}).
 * Todos los valores se pueden sobrescribir con variables de entorno, por ejemplo
 * {@code APP_JWT_SECRET} o {@code APP_GOOGLE_CLIENT_IDS}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:3000") String webBaseUrl,
        @DefaultValue("America/Mexico_City") ZoneId timezone,
        @DefaultValue Jwt jwt,
        @DefaultValue Google google,
        @DefaultValue Auth auth,
        @DefaultValue Mail mail,
        @DefaultValue Game game,
        @DefaultValue Ranking ranking,
        @DefaultValue Cors cors,
        @DefaultValue RateLimit rateLimit) {

    public record Jwt(
            String secret,
            @DefaultValue("sopa-backend") String issuer,
            @DefaultValue("15m") Duration accessTtl,
            @DefaultValue("30d") Duration refreshTtl) {
    }

    /** Client IDs de Google aceptados como audiencia (Android, iOS y web). */
    public record Google(@DefaultValue("") List<String> clientIds) {
    }

    public record Auth(
            @DefaultValue("true") boolean requireEmailVerification,
            @DefaultValue("24h") Duration verifyEmailTtl,
            @DefaultValue("1h") Duration resetPasswordTtl) {
    }

    public record Mail(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("Sopa de Letras <no-reply@sopadeletras.app>") String from) {
    }

    public record Game(
            @DefaultValue("20") int maxCustomWords,
            @DefaultValue("30") int maxDailyPastDays) {
    }

    public record Ranking(
            @DefaultValue("10") int minCityPlayers,
            @DefaultValue("50") int maxRankedPerDay,
            @DefaultValue("24h") Duration offlineFinishWindow,
            @DefaultValue("30d") Duration locationChangeCooldown,
            @DefaultValue("5s") Duration clockTolerance,
            @DefaultValue("1000") long minMsPerWord,
            @DefaultValue("120") long minMsPerLetter,
            @DefaultValue("false") boolean requireIntegrity) {
    }

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
    }

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("10") int authPerMinute,
            @DefaultValue("20") int generatePerMinute,
            @DefaultValue("120") int sessionsPerMinute) {
    }
}
