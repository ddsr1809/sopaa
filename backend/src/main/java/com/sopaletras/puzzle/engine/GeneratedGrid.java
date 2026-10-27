package com.sopaletras.puzzle.engine;

import java.util.List;

/**
 * Resultado del motor.
 *
 * @param grid    una cadena por fila, con letras A-Z y Ñ
 * @param words   palabras colocadas, en el orden en que se muestran
 * @param skipped palabras que no se pudieron usar (muy largas, repetidas o sin espacio)
 */
public record GeneratedGrid(int rows, int cols, List<String> grid, List<PlacedWord> words, List<String> skipped) {
}
