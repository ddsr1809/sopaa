package com.sopaletras.puzzle.engine;

/** Las 8 direcciones en las que puede ir una palabra. Filas crecen hacia abajo. */
public enum Direction {
    RIGHT(0, 1),
    LEFT(0, -1),
    DOWN(1, 0),
    UP(-1, 0),
    DOWN_RIGHT(1, 1),
    DOWN_LEFT(1, -1),
    UP_RIGHT(-1, 1),
    UP_LEFT(-1, -1);

    public final int dr;
    public final int dc;

    Direction(int dr, int dc) {
        this.dr = dr;
        this.dc = dc;
    }
}
