package com.sopaletras.puzzle.engine;

import java.util.EnumSet;
import java.util.Set;

/**
 * Perfiles de dificultad: tamaño de la cuadrícula, cuántas palabras, en qué direcciones
 * y si las palabras se pueden cruzar. En HARD el relleno usa letras de las propias
 * palabras para que haya "señuelos".
 */
public enum Difficulty {

    EASY(10, 10, 8, EnumSet.of(Direction.RIGHT, Direction.DOWN), false, false, 1.0),
    MEDIUM(12, 12, 10, EnumSet.of(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT,
            Direction.UP_RIGHT, Direction.LEFT), true, false, 1.5),
    HARD(15, 15, 14, EnumSet.allOf(Direction.class), true, true, 2.0);

    public final int rows;
    public final int cols;
    public final int wordCount;
    public final Set<Direction> directions;
    public final boolean allowOverlap;
    public final boolean decoyFiller;
    public final double scoreMultiplier;

    Difficulty(int rows, int cols, int wordCount, Set<Direction> directions,
               boolean allowOverlap, boolean decoyFiller, double scoreMultiplier) {
        this.rows = rows;
        this.cols = cols;
        this.wordCount = wordCount;
        this.directions = directions;
        this.allowOverlap = allowOverlap;
        this.decoyFiller = decoyFiller;
        this.scoreMultiplier = scoreMultiplier;
    }

    public int maxWordLength() {
        return Math.max(rows, cols);
    }
}
