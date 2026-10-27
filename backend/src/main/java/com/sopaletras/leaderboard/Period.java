package com.sopaletras.leaderboard;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** Periodos del ranking. La semana va de lunes a domingo (ISO) en la zona horaria del juego. */
public enum Period {
    WEEK(Duration.ofDays(70)),
    MONTH(Duration.ofDays(400)),
    ALL(Duration.ZERO);

    /** Cuánto tiempo se conservan las claves en Redis (0 = siempre). */
    public final Duration ttl;

    Period(Duration ttl) {
        this.ttl = ttl;
    }

    public String key(LocalDate date) {
        return switch (this) {
            case WEEK -> String.format("w%d-%02d", date.get(IsoFields.WEEK_BASED_YEAR),
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> String.format("m%d-%02d", date.getYear(), date.getMonthValue());
            case ALL -> "all";
        };
    }

    public LocalDate start(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case ALL -> null;
        };
    }

    public LocalDate end(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTH -> date.with(TemporalAdjusters.lastDayOfMonth());
            case ALL -> null;
        };
    }
}
