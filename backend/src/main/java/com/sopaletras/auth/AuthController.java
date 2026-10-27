package com.sopaletras.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.auth.AuthDtos.AuthResponse;
import com.sopaletras.auth.AuthDtos.EmailRequest;
import com.sopaletras.auth.AuthDtos.GoogleRequest;
import com.sopaletras.auth.AuthDtos.LoginRequest;
import com.sopaletras.auth.AuthDtos.Message;
import com.sopaletras.auth.AuthDtos.RefreshRequest;
import com.sopaletras.auth.AuthDtos.RegisterRequest;
import com.sopaletras.auth.AuthDtos.RegisterResponse;
import com.sopaletras.auth.AuthDtos.ResetRequest;
import com.sopaletras.auth.AuthDtos.TokenRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registro con correo",
            description = "Si el servidor exige verificar el correo, responde `verificationRequired: true` y envía el enlace. "
                    + "Errores: EMAIL_TAKEN, ALIAS_TAKEN, ALIAS_INVALID, ALIAS_BLOCKED.")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest body) {
        return auth.register(body.email(), body.password(), body.alias(), body.deviceId());
    }

    @PostMapping("/login")
    @Operation(summary = "Inicio con correo",
            description = "Errores: INVALID_CREDENTIALS (401), EMAIL_NOT_VERIFIED (403), ACCOUNT_SUSPENDED (403). "
                    + "Con `deviceId`, las partidas anónimas del dispositivo pasan al historial (no al ranking).")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body.email(), body.password(), body.deviceId());
    }

    @PostMapping("/google")
    @Operation(summary = "Entrar o registrarse con Google",
            description = "Verifica el ID token de Google. Si el correo ya tenía cuenta, la vincula. `newUser` indica cuenta nueva.")
    public AuthResponse google(@Valid @RequestBody GoogleRequest body) {
        return auth.google(body.idToken(), body.deviceId());
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirmar correo con el token del enlace")
    public Message verify(@Valid @RequestBody TokenRequest body) {
        auth.verifyEmail(body.token());
        return new Message("Correo confirmado. Ya puedes iniciar sesión.");
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Reenviar el correo de confirmación", description = "Siempre responde 200.")
    public Message resend(@Valid @RequestBody EmailRequest body) {
        auth.resendVerification(body.email());
        return new Message("Si el correo está pendiente de confirmar, te enviamos un enlace nuevo.");
    }

    @PostMapping("/forgot")
    @Operation(summary = "Olvidé mi contraseña", description = "Siempre responde 200 para no revelar qué correos tienen cuenta.")
    public Message forgot(@Valid @RequestBody EmailRequest body) {
        auth.forgotPassword(body.email());
        return new Message("Si el correo tiene cuenta, te enviamos un enlace para cambiar la contraseña.");
    }

    @PostMapping("/reset")
    @Operation(summary = "Cambiar contraseña con el token del enlace", description = "Cierra todas las sesiones abiertas.")
    public Message reset(@Valid @RequestBody ResetRequest body) {
        auth.resetPassword(body.token(), body.newPassword());
        return new Message("Contraseña actualizada. Inicia sesión de nuevo.");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesión",
            description = "Devuelve un access token y un refresh token **nuevos**; el anterior deja de servir. "
                    + "Si un refresh token se usa dos veces, se cierra esa sesión (REFRESH_REUSED). "
                    + "La app debe hacer una sola renovación a la vez.")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión")
    public void logout(@Valid @RequestBody RefreshRequest body) {
        auth.logout(body.refreshToken());
    }
}
