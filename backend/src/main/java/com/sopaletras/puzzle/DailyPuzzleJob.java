package com.sopaletras.puzzle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sopaletras.puzzle.engine.Difficulty;

/** Deja lista la sopa del día de hoy y de mañana en las tres dificultades. */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DailyPuzzleJob {

    private static final Logger log = LoggerFactory.getLogger(DailyPuzzleJob.class);

    private final PuzzleService puzzles;

    public DailyPuzzleJob(PuzzleService puzzles) {
        this.puzzles = puzzles;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1H")
    public void run() {
        var today = puzzles.today();
        for (var date : new java.time.LocalDate[] {today, today.plusDays(1)}) {
            for (Difficulty d : Difficulty.values()) {
                try {
                    puzzles.ensureDaily(date, d);
                } catch (RuntimeException e) {
                    log.warn("No se pudo preparar la sopa del día {} {}: {}", date, d, e.getMessage());
                }
            }
        }
    }
}
