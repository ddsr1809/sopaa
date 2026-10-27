package com.sopaletras.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;
import com.sopaletras.config.AppProperties;
import com.sopaletras.location.Location;
import com.sopaletras.location.LocationRepository;

@Service
public class UserService {

    private final UserRepository users;
    private final LocationRepository locations;
    private final AliasPolicy aliasPolicy;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;

    public UserService(UserRepository users, LocationRepository locations, AliasPolicy aliasPolicy,
                       PasswordEncoder passwords, ApplicationEventPublisher events, AppProperties props, Clock clock) {
        this.users = users;
        this.locations = locations;
        this.aliasPolicy = aliasPolicy;
        this.passwords = passwords;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    /** Se publica al borrar una cuenta para que otros módulos limpien sus datos (ranking, etc.). */
    public record UserDeletedEvent(UUID userId) {
    }

    @Transactional(readOnly = true)
    public User require(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "La cuenta ya no existe."));
    }

    @Transactional(readOnly = true)
    public MeDto me(UUID id) {
        return toDto(require(id));
    }

    public MeDto toDto(User u) {
        return MeDto.of(u, locationChangeAvailableAt(u));
    }

    private Instant locationChangeAvailableAt(User u) {
        if (u.getLocationChangedAt() == null) {
            return null;
        }
        Instant next = u.getLocationChangedAt().plus(props.ranking().locationChangeCooldown());
        return next.isAfter(clock.instant()) ? next : null;
    }

    @Transactional
    public MeDto updateProfile(UUID id, String alias, String avatarUrl) {
        User u = require(id);
        if (alias != null) {
            String key = aliasPolicy.validate(alias, u);
            u.setAlias(alias.trim(), key);
        }
        if (avatarUrl != null) {
            if (!avatarUrl.isBlank() && !avatarUrl.startsWith("https://")) {
                throw ApiException.badRequest("AVATAR_INVALID", "La foto debe ser una URL https.");
            }
            u.setAvatarUrl(avatarUrl.isBlank() ? null : avatarUrl);
        }
        return toDto(u);
    }

    /**
     * La primera vez es libre; después, una vez cada 30 días para que nadie salte de ciudad
     * en ciudad buscando un ranking más fácil. Los puntos ya ganados se quedan donde se ganaron.
     */
    @Transactional
    public MeDto changeLocation(UUID id, long locationId) {
        User u = require(id);
        Location loc = locations.findById(locationId)
                .orElseThrow(() -> ApiException.notFound("LOCATION_NOT_FOUND", "La ciudad no existe."));
        if (u.getLocation() != null && u.getLocation().getId().equals(loc.getId())) {
            return toDto(u);
        }
        Instant availableAt = locationChangeAvailableAt(u);
        if (u.getLocation() != null && availableAt != null) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "LOCATION_CHANGE_TOO_SOON",
                    "Solo puedes cambiar de ciudad una vez cada "
                            + props.ranking().locationChangeCooldown().toDays() + " días.");
        }
        // El primer registro de ciudad no activa la espera
        u.setLocation(loc, u.getLocation() == null ? null : clock.instant());
        return toDto(u);
    }

    @Transactional
    public void setPassword(UUID id, String currentPassword, String newPassword) {
        User u = require(id);
        if (u.getPasswordHash() != null
                && (currentPassword == null || !passwords.matches(currentPassword, u.getPasswordHash()))) {
            throw ApiException.forbidden("WRONG_PASSWORD", "La contraseña actual no es correcta.");
        }
        u.setPasswordHash(passwords.encode(newPassword));
    }

    /**
     * Borra la cuenta (requisito de Google Play y App Store): datos personales fuera,
     * las partidas quedan anónimas y el usuario sale de los rankings.
     */
    @Transactional
    public void delete(UUID id) {
        User u = require(id);
        events.publishEvent(new UserDeletedEvent(u.getId()));
        users.delete(u);
    }
}
