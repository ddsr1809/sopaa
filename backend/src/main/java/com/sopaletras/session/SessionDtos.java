package com.sopaletras.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sopaletras.puzzle.PuzzleKind;
import com.sopaletras.puzzle.engine.Difficulty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class SessionDtos {

    private SessionDtos() {
    }

    public record StartRequest(@NotNull UUID puzzleId) {
    }

    /**
     * {@code ranked}: si la partida puede contar para el ranking al terminarla.
     * Si es false, {@code unrankedReason} dice por qué (para mostrar "regístrate para competir", etc.).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StartResponse(UUID sessionId, UUID puzzleId, Instant startedAt, Instant finishBy, boolean ranked,
                                String unrankedReason) {
    }

    public record FinishRequest(
            @NotNull @Size(max = 100) List<@Valid Finding> findings,
            @PositiveOrZero @Max(100) int hints,
            @PositiveOrZero long elapsedMs,
            @Size(max = 4096) String integrityToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FinishResponse(
            UUID sessionId,
            int score,
            ScoreCalculator.Breakdown breakdown,
            int wordsFound,
            int totalWords,
            boolean completed,
            boolean ranked,
            String unrankedReason) {
    }

    public record SessionSummary(
            UUID id,
            UUID puzzleId,
            String puzzleTitle,
            PuzzleKind kind,
            Difficulty difficulty,
            Instant startedAt,
            Instant finishedAt,
            int score,
            int wordsFound,
            boolean completed,
            boolean ranked) {
    }
}
