package com.sopaletras.user;

import java.time.Instant;
import java.util.UUID;

import com.sopaletras.location.LocationDto;

/**
 * Perfil del usuario autenticado. {@code profileComplete=false} indica que la app debe
 * mostrar el onboarding (alias y ciudad) antes de dejarlo aparecer en el ranking.
 */
public record MeDto(
        UUID id,
        String email,
        boolean emailVerified,
        String alias,
        String avatarUrl,
        LocationDto location,
        Role role,
        boolean profileComplete,
        boolean hasPassword,
        boolean googleLinked,
        Instant locationChangeAvailableAt,
        Instant createdAt) {

    public static MeDto of(User u, Instant locationChangeAvailableAt) {
        return new MeDto(u.getId(), u.getEmail(), u.isEmailVerified(), u.getAlias(), u.getAvatarUrl(),
                LocationDto.of(u.getLocation()), u.getRole(), u.isProfileComplete(), u.getPasswordHash() != null,
                u.getGoogleSub() != null, locationChangeAvailableAt, u.getCreatedAt());
    }
}
