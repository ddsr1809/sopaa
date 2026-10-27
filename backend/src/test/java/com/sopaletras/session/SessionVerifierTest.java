package com.sopaletras.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sopaletras.config.AppProperties;
import com.sopaletras.puzzle.engine.Difficulty;
import com.sopaletras.puzzle.engine.Direction;
import com.sopaletras.puzzle.engine.PlacedWord;

class SessionVerifierTest {

    private static final AppProperties.Ranking CFG = new AppProperties.Ranking(10, 50, Duration.ofHours(24),
            Duration.ofDays(30), Duration.ofSeconds(5), 1000, 120, false);

    private static final List<PlacedWord> PLACED = List.of(
            new PlacedWord("GATO", "Gato", 0, 0, Direction.RIGHT),
            new PlacedWord("PERRO", "Perro", 1, 0, Direction.RIGHT),
            new PlacedWord("LEON", "León", 2, 0, Direction.DOWN),
            new PlacedWord("OSO", "Oso", 2, 5, Direction.LEFT),
            new PlacedWord("LOBO", "Lobo", 6, 6, Direction.UP_LEFT),
            new PlacedWord("PUMA", "Puma", 3, 3, Direction.DOWN_RIGHT));

    private final SessionVerifier verifier = new SessionVerifier(CFG);

    private static Finding f(PlacedWord w, long ms, boolean reversed) {
        int[] s = {w.row(), w.col()};
        int[] e = {w.endRow(), w.endCol()};
        return reversed ? new Finding(w.display(), e, s, ms) : new Finding(w.display(), s, e, ms);
    }

    private static List<Finding> humanLike() {
        long[] times = {4_100, 9_800, 13_200, 21_900, 26_400, 37_000};
        List<Finding> out = new ArrayList<>();
        for (int i = 0; i < PLACED.size(); i++) {
            out.add(f(PLACED.get(i), times[i], i % 2 == 1));
        }
        return out;
    }

    @Test
    void partidaNormalNoEsSospechosa() {
        var r = verifier.verify(PLACED, humanLike(), 40_000, Duration.ofSeconds(41));
        assertThat(r.suspicious()).isFalse();
        assertThat(r.foundWords()).hasSize(6);
        assertThat(r.invalid()).isZero();
    }

    @Test
    void repetidasSeIgnoranYPosicionFalsaEsSospechosa() {
        List<Finding> list = new ArrayList<>(humanLike());
        list.add(f(PLACED.get(0), 39_000, false));
        var dup = verifier.verify(PLACED, list, 40_000, Duration.ofSeconds(41));
        assertThat(dup.foundWords()).hasSize(6);
        assertThat(dup.suspicious()).isFalse();

        list.add(new Finding("GATO", new int[] {5, 5}, new int[] {5, 8}, 39_500));
        var bad = verifier.verify(PLACED, list, 40_000, Duration.ofSeconds(41));
        assertThat(bad.suspicionReason()).isEqualTo("INVALID_FINDING");
    }

    @Test
    void demasiadoRapido() {
        List<Finding> list = List.of(f(PLACED.get(0), 600, false), f(PLACED.get(1), 1_400, false));
        var r = verifier.verify(PLACED, list, 1_500, Duration.ofSeconds(2));
        assertThat(r.suspicionReason()).isEqualTo("TOO_FAST");
    }

    @Test
    void declaraMasTiempoDelQuePaso() {
        var r = verifier.verify(PLACED, humanLike(), 600_000, Duration.ofSeconds(41));
        assertThat(r.suspicionReason()).isEqualTo("CLOCK");
    }

    @Test
    void intervalosIdenticosParecenBot() {
        List<Finding> list = new ArrayList<>();
        for (int i = 0; i < PLACED.size(); i++) {
            list.add(f(PLACED.get(i), 5_000L * (i + 1), false));
        }
        var r = verifier.verify(PLACED, list, 31_000, Duration.ofSeconds(32));
        assertThat(r.suspicionReason()).isEqualTo("ROBOTIC");
    }

    @Test
    void puntaje() {
        var easy = ScoreCalculator.calculate(List.of("GATO", "PERRO"), 6, 60_000, 0, Difficulty.EASY);
        assertThat(easy.total()).isEqualTo(140 + 150);
        assertThat(easy.completionBonus()).isZero();

        // 6 palabras, completó en 100 s: objetivo 180 s → 80 s × 5 = 400 de bono
        List<String> all = PLACED.stream().map(PlacedWord::word).toList();
        var hard = ScoreCalculator.calculate(all, 6, 100_000, 1, Difficulty.HARD);
        int words = all.stream().mapToInt(w -> 100 + 10 * w.length()).sum();
        assertThat(hard.timeBonus()).isEqualTo(400);
        assertThat(hard.total()).isEqualTo((words + 200 + 400) * 2 - 50);
    }
}
