package com.sopaletras.puzzle.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.sopaletras.common.TextNormalizer;

/**
 * Motor de generación. Es <b>determinista</b>: la misma lista de palabras, la misma
 * dificultad y la misma semilla producen exactamente la misma sopa. Así la sopa del día
 * es idéntica para todos y una sopa compartida se puede reconstruir.
 *
 * <ol>
 *   <li>Normaliza y depura las palabras (repetidas, demasiado largas, contenidas en otra).</li>
 *   <li>Coloca de la más larga a la más corta, eligiendo al azar entre las posiciones válidas.</li>
 *   <li>Rellena el resto con letras (frecuencias del español, o señuelos en HARD).</li>
 *   <li>Revisa que el relleno no forme otra copia de una palabra ni una palabra bloqueada;
 *       si pasa, vuelve a rellenar.</li>
 * </ol>
 */
public class GridGenerator {

    public static final int MIN_WORD_LENGTH = 3;
    private static final char EMPTY = 0;
    private static final int MAX_FILL_ATTEMPTS = 30;

    /** Letras ponderadas aproximadamente por su frecuencia en español. */
    private static final String SPANISH_WEIGHTED =
            "EEEEEEEEEEEEAAAAAAAAAAAAOOOOOOOOOSSSSSSSRRRRRRRNNNNNNNIIIIIIDDDDDLLLLLCCCCTTTTUUUUMMMPPBBGGVYQHFZJÑXKW";

    public GeneratedGrid generate(List<String> candidates, Difficulty difficulty, long seed) {
        return generate(candidates, difficulty, seed, Set.of());
    }

    /**
     * @param candidates palabras posibles en orden de preferencia (como se muestran); se usan
     *                   hasta {@code difficulty.wordCount}
     * @param forbidden  palabras normalizadas que el relleno no debe formar por accidente
     */
    public GeneratedGrid generate(List<String> candidates, Difficulty difficulty, long seed, Set<String> forbidden) {
        return generate(candidates, difficulty, seed, forbidden, difficulty.wordCount);
    }

    /** Igual que el anterior, pero coloca hasta {@code maxWords} (para listas propias del usuario). */
    public GeneratedGrid generate(List<String> candidates, Difficulty difficulty, long seed, Set<String> forbidden,
                                  int maxWords) {
        Random rng = new Random(seed);
        int rows = difficulty.rows;
        int cols = difficulty.cols;
        char[][] grid = new char[rows][cols];
        List<String> skipped = new ArrayList<>();

        Map<String, String> pool = prepare(candidates, difficulty, skipped); // normalizada → display
        List<Map.Entry<String, String>> remaining = new ArrayList<>(pool.entrySet());

        List<PlacedWord> placed = new ArrayList<>();
        while (placed.size() < maxWords && !remaining.isEmpty()) {
            int take = Math.min(maxWords - placed.size(), remaining.size());
            List<Map.Entry<String, String>> batch = new ArrayList<>(remaining.subList(0, take));
            remaining.subList(0, take).clear();
            batch.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            for (Map.Entry<String, String> e : batch) {
                PlacedWord pw = place(grid, e.getKey(), e.getValue(), difficulty, rng);
                if (pw == null) {
                    skipped.add(e.getValue());
                } else {
                    placed.add(pw);
                }
            }
        }

        boolean[][] wordCell = new boolean[rows][cols];
        for (PlacedWord pw : placed) {
            for (int i = 0; i < pw.word().length(); i++) {
                wordCell[pw.row() + pw.direction().dr * i][pw.col() + pw.direction().dc * i] = true;
            }
        }
        String decoys = decoyLetters(placed);

        for (int attempt = 0; attempt < MAX_FILL_ATTEMPTS; attempt++) {
            fill(grid, wordCell, difficulty, decoys, rng);
            if (!hasAccidentalMatches(grid, wordCell, placed, forbidden)) {
                break;
            }
        }

        // Orden de la lista que ve el jugador: alfabético, para no delatar la colocación
        List<PlacedWord> ordered = new ArrayList<>(placed);
        ordered.sort(Comparator.comparing(PlacedWord::word));

        List<String> lines = new ArrayList<>(rows);
        for (char[] row : grid) {
            lines.add(new String(row));
        }
        return new GeneratedGrid(rows, cols, List.copyOf(lines), List.copyOf(ordered), List.copyOf(skipped));
    }

    // ------------------------------------------------------------------ preparación

    private Map<String, String> prepare(List<String> candidates, Difficulty d, List<String> skipped) {
        Map<String, String> accepted = new LinkedHashMap<>();
        for (String display : candidates) {
            String norm = TextNormalizer.forGrid(display);
            if (norm.length() < MIN_WORD_LENGTH || norm.length() > d.maxWordLength()
                    || accepted.containsKey(norm) || overlapsExisting(norm, accepted.keySet())) {
                skipped.add(display);
                continue;
            }
            accepted.put(norm, display.trim());
        }
        return accepted;
    }

    /** Si una palabra está contenida en otra (o en su reverso) siempre aparecería dos veces. */
    private static boolean overlapsExisting(String word, Collection<String> existing) {
        String reversed = new StringBuilder(word).reverse().toString();
        for (String other : existing) {
            String otherRev = new StringBuilder(other).reverse().toString();
            if (other.contains(word) || other.contains(reversed) || word.contains(other) || word.contains(otherRev)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ colocación

    private PlacedWord place(char[][] grid, String word, String display, Difficulty d, Random rng) {
        List<int[]> options = new ArrayList<>();
        List<Direction> dirs = new ArrayList<>(d.directions);
        for (int di = 0; di < dirs.size(); di++) {
            Direction dir = dirs.get(di);
            for (int r = 0; r < d.rows; r++) {
                for (int c = 0; c < d.cols; c++) {
                    int overlap = fits(grid, word, r, c, dir, d.allowOverlap);
                    if (overlap >= 0) {
                        options.add(new int[] {r, c, di, overlap});
                    }
                }
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        int[] choice;
        // Con cruces permitidos, a veces se prefiere una posición que comparta letras
        if (d.allowOverlap && rng.nextDouble() < 0.4) {
            List<int[]> crossing = options.stream().filter(o -> o[3] > 0).toList();
            choice = crossing.isEmpty() ? options.get(rng.nextInt(options.size()))
                    : crossing.get(rng.nextInt(crossing.size()));
        } else {
            choice = options.get(rng.nextInt(options.size()));
        }
        Direction dir = dirs.get(choice[2]);
        for (int i = 0; i < word.length(); i++) {
            grid[choice[0] + dir.dr * i][choice[1] + dir.dc * i] = word.charAt(i);
        }
        return new PlacedWord(word, display, choice[0], choice[1], dir);
    }

    /** -1 si no cabe; si cabe, cuántas letras comparte con palabras ya puestas. */
    private static int fits(char[][] grid, String word, int r, int c, Direction dir, boolean allowOverlap) {
        int rows = grid.length;
        int cols = grid[0].length;
        int endR = r + dir.dr * (word.length() - 1);
        int endC = c + dir.dc * (word.length() - 1);
        if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) {
            return -1;
        }
        int overlap = 0;
        for (int i = 0; i < word.length(); i++) {
            char existing = grid[r + dir.dr * i][c + dir.dc * i];
            if (existing == EMPTY) {
                continue;
            }
            if (!allowOverlap || existing != word.charAt(i)) {
                return -1;
            }
            overlap++;
        }
        // No permitir que una palabra quede completamente encima de otra
        return overlap == word.length() ? -1 : overlap;
    }

    // ------------------------------------------------------------------ relleno

    private static String decoyLetters(List<PlacedWord> placed) {
        StringBuilder sb = new StringBuilder();
        placed.forEach(p -> sb.append(p.word()));
        return sb.isEmpty() ? SPANISH_WEIGHTED : sb.toString();
    }

    private static void fill(char[][] grid, boolean[][] wordCell, Difficulty d, String decoys, Random rng) {
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (wordCell[r][c]) {
                    continue;
                }
                boolean useDecoy = d.decoyFiller && rng.nextDouble() < 0.5;
                String source = useDecoy ? decoys : SPANISH_WEIGHTED;
                grid[r][c] = source.charAt(rng.nextInt(source.length()));
            }
        }
    }

    /**
     * ¿El relleno formó otra aparición de alguna palabra o una palabra prohibida?
     * Solo cuenta si la aparición usa al menos una celda de relleno.
     */
    private static boolean hasAccidentalMatches(char[][] grid, boolean[][] wordCell,
                                                List<PlacedWord> placed, Set<String> forbidden) {
        List<String> targets = new ArrayList<>(placed.size() + forbidden.size());
        placed.forEach(p -> targets.add(p.word()));
        targets.addAll(forbidden);
        int rows = grid.length;
        int cols = grid[0].length;
        for (String target : targets) {
            int len = target.length();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (grid[r][c] != target.charAt(0)) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        int endR = r + dir.dr * (len - 1);
                        int endC = c + dir.dc * (len - 1);
                        if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) {
                            continue;
                        }
                        boolean match = true;
                        boolean usesFiller = false;
                        for (int i = 0; i < len && match; i++) {
                            int rr = r + dir.dr * i;
                            int cc = c + dir.dc * i;
                            match = grid[rr][cc] == target.charAt(i);
                            usesFiller |= !wordCell[rr][cc];
                        }
                        if (match && usesFiller) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
