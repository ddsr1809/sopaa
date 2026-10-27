package com.sopaletras.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.sopaletras.common.ApiException;

/** Acceso al usuario autenticado de la petición actual (si lo hay). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UUID> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
        }
        return Optional.empty();
    }

    public static UUID require() {
        return id().orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Necesitas iniciar sesión."));
    }
}
