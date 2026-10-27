package com.sopaletras.auth;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sopaletras.user.MeDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de /api/v1/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String password,
            @Size(max = 20) String alias,
            String deviceId) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, String deviceId) {
    }

    /** {@code idToken}: el que entrega Credential Manager (Android), GoogleSignIn (iOS) o Google Identity Services (web). */
    public record GoogleRequest(@NotBlank String idToken, String deviceId) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record ResetRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String newPassword) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /**
     * Respuesta de inicio de sesión. Si {@code user.profileComplete} es false, la app muestra
     * el onboarding (alias y ciudad).
     */
    public record AuthResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            String tokenType,
            boolean newUser,
            MeDto user) {
    }

    /** Si {@code verificationRequired}, la app pide revisar el correo; si no, ya trae la sesión. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegisterResponse(boolean verificationRequired, AuthResponse session) {
    }

    public record Message(String message) {
    }
}
