package com.sopaletras.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    /** Invalida los tokens pendientes del mismo tipo al emitir uno nuevo. */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidatePending(@Param("userId") UUID userId, @Param("type") EmailToken.Type type, @Param("now") Instant now);
}
