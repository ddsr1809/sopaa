package com.sopaletras.session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sopaletras.common.TextNormalizer;
import com.sopaletras.config.AppProperties;
import com.sopaletras.puzzle.engine.PlacedWord;

/**
 * Revisión anti-trampa de una partida terminada. Una sopa de letras siempre se puede resolver
 * con un programa; la meta es que hacer trampa no valga la pena y que lo raro se detecte.
 * <ul>
 *   <li>Cada hallazgo debe coincidir con la posición real de la palabra (en cualquier sentido).</li>
 *   <li>El tiempo declarado no puede ser mayor que el tiempo real desde que el servidor inició la partida.</li>
 *   <li>Hay un tiempo mínimo humano por palabra y por letra.</li>
 *   <li>Intervalos casi idénticos entre hallazgos parecen automatizados.</li>
 * </ul>
 * Las partidas sospechosas se guardan pero no suman al ranking y quedan para revisión.
 */
public final class SessionVerifier {

    static final int ROBOTIC_MIN_WORDS = 5;
    static final double ROBOTIC_MAX_STDDEV_MS = 80;

    private final AppProperties.Ranking cfg;

    public SessionVerifier(AppProperties.Ranking cfg) {
        this.cfg = cfg;
    }

    public record Result(List<String> foundWords, List<Finding> accepted, int invalid, String suspicionReason) {
        public boolean suspicious() {
            return suspicionReason != null;
        }
    }

    /**
     * @param serverElapsed tiempo real entre el inicio (hora del servidor) y ahora
     */
    public Result verify(List<PlacedWord> placed, List<Finding> findings, long claimedMs, Duration serverElapsed) {
        Map<String, PlacedWord> byWord = new LinkedHashMap<>();
        placed.forEach(p -> byWord.put(p.word(), p));

        List<String> found = new ArrayList<>();
        List<Finding> accepted = new ArrayList<>();
        int invalid = 0;
        String reason = null;

        for (Finding f : findings) {
            String w = TextNormalizer.forGrid(f.word());
            PlacedWord p = byWord.get(w);
            if (p == null || !p.matches(f.start()[0], f.start()[1], f.end()[0], f.end()[1])) {
                invalid++;
                continue;
            }
            if (found.contains(w)) {
                continue; // repetida: se ignora
            }
            found.add(w);
            accepted.add(new Finding(w, f.start(), f.end(), f.msFromStart()));
        }
        if (invalid > 0) {
            reason = "INVALID_FINDING";
        }

        long tolerance = cfg.clockTolerance().toMillis();
        if (reason == null && claimedMs > serverElapsed.toMillis() + tolerance) {
            reason = "CLOCK"; // declara más tiempo del que pudo pasar
        }
        if (reason == null) {
            for (Finding f : accepted) {
                if (f.msFromStart() > claimedMs + tolerance) {
                    reason = "TIMELINE";
                    break;
                }
            }
        }
        if (reason == null && !found.isEmpty()) {
            long minimum = found.stream().mapToLong(w -> cfg.minMsPerWord() + cfg.minMsPerLetter() * w.length()).sum();
            if (claimedMs < minimum || serverElapsed.toMillis() + tolerance < minimum) {
                reason = "TOO_FAST";
            }
        }
        if (reason == null && accepted.size() >= ROBOTIC_MIN_WORDS && looksRobotic(accepted)) {
            reason = "ROBOTIC";
        }
        return new Result(found, accepted, invalid, reason);
    }

    private static boolean looksRobotic(List<Finding> accepted) {
        long[] times = accepted.stream().mapToLong(Finding::msFromStart).sorted().toArray();
        double[] deltas = new double[times.length - 1];
        double sum = 0;
        for (int i = 1; i < times.length; i++) {
            deltas[i - 1] = times[i] - times[i - 1];
            sum += deltas[i - 1];
        }
        double mean = sum / deltas.length;
        double var = 0;
        for (double d : deltas) {
            var += (d - mean) * (d - mean);
        }
        double stddev = Math.sqrt(var / deltas.length);
        return mean > 0 && stddev < ROBOTIC_MAX_STDDEV_MS;
    }
}
