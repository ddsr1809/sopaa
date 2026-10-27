package com.sopaletras.puzzle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sopaletras.puzzle.engine.Difficulty;
import com.sopaletras.puzzle.engine.Direction;
import com.sopaletras.puzzle.engine.PlacedWord;

/**
 * Contrato de una sopa. Incluye las posiciones de las palabras para que la app valide
 * al instante y sin conexión; el puntaje oficial lo recalcula el servidor al terminar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PuzzleDto(
        UUID id,
        PuzzleKind kind,
        String title,
        TopicRef topic,
        Difficulty difficulty,
        Size size,
        List<String> grid,
        List<WordDto> words,
        Source source,
        LocalDate dailyDate,
        Instant createdAt,
        List<String> skippedWords) {

    /** Solo en sopas personalizadas: palabras que no cupieron o se repetían. */
    public PuzzleDto withSkipped(List<String> skipped) {
        return new PuzzleDto(id, kind, title, topic, difficulty, size, grid, words, source, dailyDate, createdAt,
                skipped.isEmpty() ? null : skipped);
    }

    public record TopicRef(String slug, String name, String category) {
    }

    public record Size(int rows, int cols) {
    }

    /** {@code start} y {@code end} son [fila, columna] desde 0. */
    public record WordDto(String word, String display, int[] start, int[] end, Direction direction) {
    }

    public record Source(String type, String title, String url) {
    }

    public static PuzzleDto of(Puzzle p) {
        TopicRef topic = p.getTopic() == null ? null
                : new TopicRef(p.getTopic().getSlug(), p.getTopic().getName(), p.getTopic().getCategory());
        List<WordDto> words = p.placedWords().stream()
                .map((PlacedWord w) -> new WordDto(w.word(), w.display(), new int[] {w.row(), w.col()},
                        new int[] {w.endRow(), w.endCol()}, w.direction()))
                .toList();
        Source source = p.getSourceType() == null ? null
                : new Source(p.getSourceType(), p.getSourceTitle(), p.getSourceUrl());
        return new PuzzleDto(p.getId(), p.getKind(), p.getTitle(), topic, p.getDifficulty(),
                new Size(p.getRows(), p.getCols()), p.getGrid(), words, source, p.getDailyDate(), p.getCreatedAt(), null);
    }
}
