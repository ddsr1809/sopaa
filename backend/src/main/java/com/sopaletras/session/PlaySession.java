package com.sopaletras.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "play_sessions")
public class PlaySession implements Persistable<UUID> {

    public enum Status { STARTED, FINISHED }

    @Id
    private UUID id;

    @Column(name = "puzzle_id", nullable = false)
    private UUID puzzleId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "location_id")
    private Long locationId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.STARTED;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "claimed_ms")
    private Long claimedMs;

    @Column(nullable = false)
    private int hints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Finding> findings;

    @Column(name = "words_found", nullable = false)
    private int wordsFound;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private boolean ranked;

    @Column(nullable = false)
    private boolean suspicious;

    @Column(name = "suspicion_reason")
    private String suspicionReason;

    @Column(name = "unranked_reason")
    private String unrankedReason;

    @Transient
    private boolean isNew = true;

    protected PlaySession() {
    }

    public PlaySession(UUID puzzleId, UUID userId, String deviceId, Instant startedAt, String unrankedReason) {
        this.id = UUID.randomUUID();
        this.puzzleId = puzzleId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.startedAt = startedAt;
        this.unrankedReason = unrankedReason;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void finish(Instant at, long claimedMs, int hints, List<Finding> findings, int wordsFound, boolean completed,
                       int score) {
        this.status = Status.FINISHED;
        this.finishedAt = at;
        this.claimedMs = claimedMs;
        this.hints = hints;
        this.findings = findings;
        this.wordsFound = wordsFound;
        this.completed = completed;
        this.score = score;
    }

    public void markRanked(Long locationId) {
        this.ranked = true;
        this.locationId = locationId;
        this.unrankedReason = null;
    }

    public void markUnranked(String reason) {
        this.ranked = false;
        this.unrankedReason = reason;
    }

    public void markSuspicious(String reason) {
        this.suspicious = true;
        this.suspicionReason = reason;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPuzzleId() {
        return puzzleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getWordsFound() {
        return wordsFound;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getScore() {
        return score;
    }

    public boolean isRanked() {
        return ranked;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public String getUnrankedReason() {
        return unrankedReason;
    }
}
