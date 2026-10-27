package com.sopaletras.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;
import com.sopaletras.common.Tokens;
import com.sopaletras.config.AppProperties;
import com.sopaletras.device.DeviceService;
import com.sopaletras.user.AliasPolicy;
import com.sopaletras.user.User;
import com.sopaletras.user.UserRepository;
import com.sopaletras.user.UserService;

@Service
public class AuthService {

    /** Hash de relleno para que "correo no existe" tarde lo mismo que "contraseña incorrecta". */
    private final String dummyHash;

    private final UserRepository users;
    private final UserService userService;
    private final AliasPolicy aliasPolicy;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final EmailTokenRepository emailTokens;
    private final MailService mail;
    private final GoogleTokenVerifier google;
    private final DeviceService devices;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, UserService userService, AliasPolicy aliasPolicy,
                       PasswordEncoder passwords, TokenService tokens, EmailTokenRepository emailTokens,
                       MailService mail, GoogleTokenVerifier google, DeviceService devices,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.userService = userService;
        this.aliasPolicy = aliasPolicy;
        this.passwords = passwords;
        this.tokens = tokens;
        this.emailTokens = emailTokens;
        this.mail = mail;
        this.google = google;
        this.devices = devices;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwords.encode(Tokens.random());
    }

    // ------------------------------------------------------------------ correo y contraseña

    @Transactional
    public AuthDtos.RegisterResponse register(String rawEmail, String password, String alias, String deviceId) {
        String email = normalizeEmail(rawEmail);
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            boolean googleOnly = existing.get().getPasswordHash() == null;
            throw ApiException.conflict("EMAIL_TAKEN", googleOnly
                    ? "Ese correo ya tiene cuenta con Google. Entra con Google."
                    : "Ese correo ya tiene cuenta. Inicia sesión o recupera tu contraseña.");
        }
        Instant now = clock.instant();
        User u = new User(email, now);
        u.setPasswordHash(passwords.encode(password));
        if (alias != null && !alias.isBlank()) {
            u.setAlias(alias.trim(), aliasPolicy.validate(alias, null));
        }
        users.save(u);

        if (props.auth().requireEmailVerification()) {
            sendVerification(u, now);
            return new AuthDtos.RegisterResponse(true, null);
        }
        return new AuthDtos.RegisterResponse(false, startSession(u, deviceId, true));
    }

    @Transactional
    public void verifyEmail(String token) {
        Instant now = clock.instant();
        EmailToken t = emailTokens.findByTokenHash(Tokens.sha256(token))
                .filter(x -> x.isUsable(EmailToken.Type.VERIFY_EMAIL, now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido o ya venció."));
        t.markUsed(now);
        users.findById(t.getUserId()).ifPresent(u -> u.setEmailVerified(true));
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail))
                .filter(u -> !u.isEmailVerified() && u.getPasswordHash() != null)
                .ifPresent(u -> sendVerification(u, clock.instant()));
    }

    @Transactional
    public AuthDtos.AuthResponse login(String rawEmail, String password, String deviceId) {
        Optional<User> found = users.findByEmail(normalizeEmail(rawEmail));
        String hash = found.map(User::getPasswordHash).orElse(null);
        boolean ok = passwords.matches(password, hash == null ? dummyHash : hash) && hash != null;
        if (!ok) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
        }
        User u = found.get();
        if (props.auth().requireEmailVerification() && !u.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Confirma tu correo para entrar. Revisa tu bandeja.");
        }
        return startSession(u, deviceId, false);
    }

    /** Siempre responde igual, exista o no el correo, para no revelar quién tiene cuenta. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail)).filter(User::isActive).ifPresent(u -> {
            Instant now = clock.instant();
            emailTokens.invalidatePending(u.getId(), EmailToken.Type.RESET_PASSWORD, now);
            String token = Tokens.random();
            emailTokens.save(new EmailToken(u.getId(), EmailToken.Type.RESET_PASSWORD, Tokens.sha256(token),
                    now.plus(props.auth().resetPasswordTtl()), now));
            mail.sendPasswordReset(u.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        EmailToken t = emailTokens.findByTokenHash(Tokens.sha256(token))
                .filter(x -> x.isUsable(EmailToken.Type.RESET_PASSWORD, now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido o ya venció."));
        t.markUsed(now);
        User u = userService.require(t.getUserId());
        u.setPasswordHash(passwords.encode(newPassword));
        u.setEmailVerified(true); // abrió el enlace de su correo
        tokens.revokeAll(u.getId());
    }

    // ------------------------------------------------------------------ Google

    @Transactional
    public AuthDtos.AuthResponse google(String idToken, String deviceId) {
        GoogleTokenVerifier.GoogleIdentity id = google.verify(idToken);
        Instant now = clock.instant();

        Optional<User> bySub = users.findByGoogleSub(id.subject());
        if (bySub.isPresent()) {
            return startSession(bySub.get(), deviceId, false);
        }

        String email = id.email() == null ? null : normalizeEmail(id.email());
        Optional<User> byEmail = email != null && id.emailVerified() ? users.findByEmail(email) : Optional.empty();
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            if (!u.isEmailVerified()) {
                // Alguien se registró con este correo sin confirmarlo: Google demuestra quién es el dueño,
                // así que se descarta esa contraseña y se cierran sus sesiones (evita secuestro de cuenta).
                u.setPasswordHash(null);
                tokens.revokeAll(u.getId());
            }
            u.setGoogleSub(id.subject());
            u.setEmailVerified(true);
            if (u.getAvatarUrl() == null) {
                u.setAvatarUrl(id.picture());
            }
            return startSession(u, deviceId, false);
        }

        if (email != null && users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ese correo ya tiene una cuenta. Inicia sesión con tu contraseña.");
        }
        User u = new User(email, now);
        u.setGoogleSub(id.subject());
        u.setEmailVerified(id.emailVerified());
        u.setAvatarUrl(id.picture());
        users.save(u);
        return startSession(u, deviceId, true);
    }

    // ------------------------------------------------------------------ sesión

    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.AuthResponse refresh(String refreshToken) {
        TokenService.RotateResult r = tokens.rotate(refreshToken);
        User u = userService.require(r.userId());
        if (!u.isActive()) {
            tokens.revokeAll(u.getId());
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Tu cuenta está suspendida.");
        }
        return response(u, tokens.issue(u, r.family()), false);
    }

    @Transactional
    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    private AuthDtos.AuthResponse startSession(User u, String deviceId, boolean newUser) {
        if (!u.isActive()) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Tu cuenta está suspendida.");
        }
        if (deviceId != null) {
            devices.claimAnonymousPlays(deviceId, u.getId());
        }
        return response(u, tokens.issue(u), newUser);
    }

    private AuthDtos.AuthResponse response(User u, TokenService.Issued t, boolean newUser) {
        return new AuthDtos.AuthResponse(t.accessToken(), t.accessExpiresAt(), t.refreshToken(), t.refreshExpiresAt(),
                "Bearer", newUser, userService.toDto(u));
    }

    private void sendVerification(User u, Instant now) {
        emailTokens.invalidatePending(u.getId(), EmailToken.Type.VERIFY_EMAIL, now);
        String token = Tokens.random();
        emailTokens.save(new EmailToken(u.getId(), EmailToken.Type.VERIFY_EMAIL, Tokens.sha256(token),
                now.plus(props.auth().verifyEmailTtl()), now));
        mail.sendVerification(u.getEmail(), u.getAlias(), token);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
