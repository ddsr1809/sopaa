package com.sopaletras.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PlaySessionRepository extends JpaRepository<PlaySession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlaySession s where s.id = :id")
    Optional<PlaySession> findForUpdate(@Param("id") UUID id);

    boolean existsByUserIdAndPuzzleIdAndRankedTrue(UUID userId, UUID puzzleId);

    long countByUserIdAndRankedTrueAndFinishedAtAfter(UUID userId, Instant since);

    Page<PlaySession> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);
}
