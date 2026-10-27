package com.sopaletras.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Una palabra encontrada por el jugador: qué palabra, de qué celda a qué celda ([fila, columna])
 * y en qué momento desde que empezó la partida.
 */
public record Finding(
        @NotBlank @Size(max = 60) String word,
        @NotNull @Size(min = 2, max = 2) int[] start,
        @NotNull @Size(min = 2, max = 2) int[] end,
        @PositiveOrZero long msFromStart) {
}
