package com.sopaletras.leaderboard;

import java.time.Instant;
import java.util.UUID;

/** Se publica cuando una partida queda aceptada para el ranking; se aplica a Redis después del commit. */
public record PointsAwardedEvent(UUID userId, Long locationId, int points, Instant at) {
}
