package com.sopaletras.session;

import java.util.List;

import com.sopaletras.puzzle.engine.Difficulty;

/**
 * Puntaje oficial (lo calcula el servidor; el que muestre la app es solo una vista previa):
 * <pre>
 *   palabras   = Σ (100 + 10 × letras)
 *   completar  = 200 si encontró todas
 *   tiempo     = 5 × segundos por debajo de (30 s × número de palabras), solo si completó
 *   subtotal   = (palabras + completar + tiempo) × multiplicador de dificultad (1 · 1.5 · 2)
 *   puntaje    = subtotal − 50 × pistas   (mínimo 0)
 * </pre>
 */
public final class ScoreCalculator {

    public static final int POINTS_PER_WORD = 100;
    public static final int POINTS_PER_LETTER = 10;
    public static final int COMPLETION_BONUS = 200;
    public static final int TARGET_SECONDS_PER_WORD = 30;
    public static final int POINTS_PER_SECOND_SAVED = 5;
    public static final int HINT_PENALTY = 50;

    private ScoreCalculator() {
    }

    public record Breakdown(int wordPoints, int completionBonus, int timeBonus, double multiplier, int hintPenalty,
                            int total) {
    }

    public static Breakdown calculate(List<String> foundWords, int totalWords, long elapsedMs, int hints,
                                      Difficulty difficulty) {
        int wordPoints = foundWords.stream().mapToInt(w -> POINTS_PER_WORD + POINTS_PER_LETTER * w.length()).sum();
        boolean completed = totalWords > 0 && foundWords.size() == totalWords;
        int completion = completed ? COMPLETION_BONUS : 0;
        int timeBonus = 0;
        if (completed) {
            long target = (long) TARGET_SECONDS_PER_WORD * totalWords;
            long seconds = elapsedMs / 1000;
            timeBonus = (int) Math.max(0, (target - seconds) * POINTS_PER_SECOND_SAVED);
        }
        double mult = difficulty.scoreMultiplier;
        int penalty = HINT_PENALTY * Math.max(0, hints);
        int total = (int) Math.max(0, Math.round((wordPoints + completion + timeBonus) * mult) - penalty);
        return new Breakdown(wordPoints, completion, timeBonus, mult, penalty, total);
    }
}
