package com.sopaletras.leaderboard;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class LeaderboardDtos {

    private LeaderboardDtos() {
    }

    /**
     * Un ranking. Si {@code hidden} es true (ciudad con pocos jugadores), {@code entries} viene vacío
     * y la app debería mostrar el de {@code fallbackScope}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Board(
            Period period,
            String periodKey,
            LocalDate startsOn,
            LocalDate endsOn,
            Scope scope,
            String scopeId,
            String scopeName,
            long totalPlayers,
            boolean hidden,
            Scope fallbackScope,
            List<Entry> entries,
            Standing me) {
    }

    /** Solo se muestra el alias público; nunca correo ni nombre real. */
    public record Entry(long rank, String alias, String avatarUrl, long points, boolean isMe) {
    }

    /** Posición de un jugador en un ámbito. {@code rank} es null si todavía no tiene puntos ahí. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Standing(Scope scope, String scopeId, String scopeName, Long rank, long points, long totalPlayers,
                           boolean hidden) {
    }

    public record MyStandings(Period period, String periodKey, LocalDate startsOn, LocalDate endsOn,
                              List<Standing> standings) {
    }
}
