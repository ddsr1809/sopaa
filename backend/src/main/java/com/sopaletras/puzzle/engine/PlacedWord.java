package com.sopaletras.puzzle.engine;

/** Una palabra ya colocada: texto normalizado, cómo se muestra, inicio y dirección. */
public record PlacedWord(String word, String display, int row, int col, Direction direction) {

    public int endRow() {
        return row + direction.dr * (word.length() - 1);
    }

    public int endCol() {
        return col + direction.dc * (word.length() - 1);
    }

    /** ¿La selección (inicio → fin) corresponde a esta palabra, leída en cualquier sentido? */
    public boolean matches(int r1, int c1, int r2, int c2) {
        return (r1 == row && c1 == col && r2 == endRow() && c2 == endCol())
                || (r2 == row && c2 == col && r1 == endRow() && c1 == endCol());
    }
}
