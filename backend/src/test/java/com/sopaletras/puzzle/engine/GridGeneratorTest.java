package com.sopaletras.puzzle.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GridGeneratorTest {

    private static final List<String> WORDS = List.of(
            "Perro", "Gato", "Caballo", "Jirafa", "Elefante", "León", "Tigre", "Delfín", "Ballena", "Águila",
            "Búho", "Tortuga", "Conejo", "Ardilla", "Pingüino", "Cocodrilo", "Serpiente", "Mariposa", "Ñandú", "Lobo");

    private final GridGenerator generator = new GridGenerator();

    @Test
    void mismaSemillaMismaSopa() {
        GeneratedGrid a = generator.generate(WORDS, Difficulty.HARD, 42L);
        GeneratedGrid b = generator.generate(WORDS, Difficulty.HARD, 42L);
        GeneratedGrid c = generator.generate(WORDS, Difficulty.HARD, 43L);
        assertThat(a).isEqualTo(b);
        assertThat(a.grid()).isNotEqualTo(c.grid());
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void cadaPalabraEstaDondeDiceYEnUnaDireccionPermitida(Difficulty d) {
        for (long seed = 0; seed < 25; seed++) {
            GeneratedGrid g = generator.generate(WORDS, d, seed);
            assertThat(g.rows()).isEqualTo(d.rows);
            assertThat(g.grid()).hasSize(d.rows).allSatisfy(row -> assertThat(row).hasSize(d.cols));
            assertThat(g.words()).hasSize(d.wordCount);
            for (PlacedWord w : g.words()) {
                assertThat(d.directions).contains(w.direction());
                StringBuilder read = new StringBuilder();
                for (int i = 0; i < w.word().length(); i++) {
                    read.append(g.grid().get(w.row() + w.direction().dr * i).charAt(w.col() + w.direction().dc * i));
                }
                assertThat(read.toString()).isEqualTo(w.word());
            }
            String all = String.join("", g.grid());
            assertThat(all).matches("[A-ZÑ]+");
        }
    }

    @Test
    void sinCrucesEnFacil() {
        GeneratedGrid g = generator.generate(WORDS, Difficulty.EASY, 7L);
        boolean[][] used = new boolean[g.rows()][g.cols()];
        for (PlacedWord w : g.words()) {
            for (int i = 0; i < w.word().length(); i++) {
                int r = w.row() + w.direction().dr * i;
                int c = w.col() + w.direction().dc * i;
                assertThat(used[r][c]).as("celda %d,%d usada dos veces", r, c).isFalse();
                used[r][c] = true;
            }
        }
    }

    @Test
    void descartaRepetidasLargasYContenidas() {
        GeneratedGrid g = generator.generate(
                List.of("Sol", "SOL", "Girasol", "Electroencefalograma", "ab", "Luna", "Marte"), Difficulty.MEDIUM, 1L);
        assertThat(g.words()).extracting(PlacedWord::word).containsExactlyInAnyOrder("SOL", "LUNA", "MARTE");
        assertThat(g.skipped()).contains("SOL", "Girasol", "Electroencefalograma", "ab");
    }

    @Test
    void elRellenoNoFormaPalabrasProhibidas() {
        for (long seed = 0; seed < 30; seed++) {
            GeneratedGrid g = generator.generate(WORDS, Difficulty.HARD, seed, Set.of("SOS"));
            assertThat(contains(g.grid(), "SOS")).as("semilla %d", seed).isFalse();
        }
    }

    @Test
    void laSeleccionInversaTambienCoincide() {
        PlacedWord w = new PlacedWord("GATO", "Gato", 2, 3, Direction.DOWN_RIGHT);
        assertThat(w.matches(2, 3, 5, 6)).isTrue();
        assertThat(w.matches(5, 6, 2, 3)).isTrue();
        assertThat(w.matches(2, 3, 5, 5)).isFalse();
    }

    private static boolean contains(List<String> grid, String word) {
        for (int r = 0; r < grid.size(); r++) {
            for (int c = 0; c < grid.get(r).length(); c++) {
                for (Direction d : Direction.values()) {
                    int i = 0;
                    while (i < word.length()) {
                        int rr = r + d.dr * i;
                        int cc = c + d.dc * i;
                        if (rr < 0 || rr >= grid.size() || cc < 0 || cc >= grid.get(rr).length()
                                || grid.get(rr).charAt(cc) != word.charAt(i)) {
                            break;
                        }
                        i++;
                    }
                    if (i == word.length()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
