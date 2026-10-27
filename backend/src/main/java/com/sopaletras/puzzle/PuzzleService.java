package com.sopaletras.puzzle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sopaletras.common.ApiException;
import com.sopaletras.config.AppProperties;
import com.sopaletras.puzzle.engine.Difficulty;
import com.sopaletras.puzzle.engine.GeneratedGrid;
import com.sopaletras.puzzle.engine.GridGenerator;
import com.sopaletras.topic.Topic;
import com.sopaletras.topic.TopicRepository;
import com.sopaletras.topic.TopicService;
import com.sopaletras.topic.TopicType;
import com.sopaletras.user.ModerationService;

@Service
public class PuzzleService {

    private static final int MIN_PLACED_WORDS = 3;
    private static final int MIN_DAILY_TOPIC_WORDS = 8;

    private final PuzzleRepository puzzles;
    private final TopicRepository topics;
    private final TopicService topicService;
    private final ModerationService moderation;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final GridGenerator generator = new GridGenerator();
    private final Random seeds = new Random();

    public PuzzleService(PuzzleRepository puzzles, TopicRepository topics, TopicService topicService,
                         ModerationService moderation, AppProperties props, Clock clock,
                         PlatformTransactionManager txManager) {
        this.puzzles = puzzles;
        this.topics = topics;
        this.topicService = topicService;
        this.moderation = moderation;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ por tema

    @Transactional
    public PuzzleDto forTopic(String slug, Difficulty difficulty) {
        Topic topic = topicService.requireActive(slug);
        long seed = seeds.nextLong();
        List<String> words = new ArrayList<>(topics.findWords(topic.getId()));
        Collections.shuffle(words, new Random(seed));
        GeneratedGrid g = generator.generate(words, difficulty, seed, moderation.blockedWords());
        if (g.words().size() < MIN_PLACED_WORDS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPIC_TOO_SMALL",
                    "Este tema todavía no tiene suficientes palabras para esa dificultad.");
        }
        Puzzle p = new Puzzle(PuzzleKind.TOPIC, topic, difficulty, seed, g, clock.instant());
        p.setTitle(topic.getName());
        p.setSource(topic.getType().name(), topic.getName(), null);
        return PuzzleDto.of(puzzles.save(p));
    }

    // ------------------------------------------------------------------ sopa del día

    public LocalDate today() {
        return LocalDate.now(clock.withZone(props.timezone()));
    }

    /** La misma para todos: se crea la primera vez que alguien la pide (o el job la adelanta). */
    public PuzzleDto daily(LocalDate requested, Difficulty difficulty) {
        LocalDate today = today();
        LocalDate date = requested == null ? today : requested;
        if (date.isAfter(today)) {
            throw ApiException.badRequest("DATE_IN_FUTURE", "La sopa de ese día todavía no está disponible.");
        }
        if (date.isBefore(today.minusDays(props.game().maxDailyPastDays()))) {
            throw ApiException.badRequest("DATE_TOO_OLD", "Esa sopa del día ya no está disponible.");
        }
        return PuzzleDto.of(ensureDaily(date, difficulty));
    }

    public Puzzle ensureDaily(LocalDate date, Difficulty difficulty) {
        Puzzle existing = tx.execute(s -> puzzles.findByKindAndDailyDateAndDifficulty(PuzzleKind.DAILY, date, difficulty)
                .map(this::initialize).orElse(null));
        if (existing != null) {
            return existing;
        }
        try {
            return tx.execute(s -> initialize(puzzles.saveAndFlush(buildDaily(date, difficulty))));
        } catch (DataIntegrityViolationException race) {
            // Otra petición la creó al mismo tiempo: usamos esa
            return tx.execute(s -> puzzles.findByKindAndDailyDateAndDifficulty(PuzzleKind.DAILY, date, difficulty)
                    .map(this::initialize).orElseThrow(() -> race));
        }
    }

    private Puzzle buildDaily(LocalDate date, Difficulty difficulty) {
        List<Topic> eligible = topics.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> t.getType() == TopicType.CURATED && t.getRegion() == null)
                .filter(t -> topics.findWords(t.getId()).size() >= MIN_DAILY_TOPIC_WORDS)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        if (eligible.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NO_TOPICS", "No hay temas disponibles.");
        }
        // Todas las dificultades del mismo día usan el mismo tema; la semilla depende solo de fecha y dificultad
        Topic topic = eligible.get((int) Math.floorMod(date.toEpochDay(), eligible.size()));
        long seed = date.toEpochDay() * 1_000_003L + difficulty.ordinal();
        List<String> words = new ArrayList<>(topics.findWords(topic.getId()));
        Collections.shuffle(words, new Random(seed));
        GeneratedGrid g = generator.generate(words, difficulty, seed, moderation.blockedWords());
        Puzzle p = new Puzzle(PuzzleKind.DAILY, topic, difficulty, seed, g, clock.instant());
        p.setDailyDate(date);
        p.setTitle("Sopa del día: " + topic.getName());
        p.setSource(topic.getType().name(), topic.getName(), null);
        return p;
    }

    // ------------------------------------------------------------------ personalizada

    @Transactional
    public PuzzleDto custom(List<String> words, Difficulty difficulty, String title, UUID userId) {
        int max = props.game().maxCustomWords();
        List<String> clean = words.stream().filter(w -> w != null && !w.isBlank()).map(String::trim).distinct().toList();
        if (clean.size() < MIN_PLACED_WORDS || clean.size() > max) {
            throw ApiException.badRequest("WORD_COUNT", "Escribe entre " + MIN_PLACED_WORDS + " y " + max + " palabras.");
        }
        for (String w : clean) {
            if (w.length() > 40 || moderation.isBlocked(w)) {
                throw ApiException.badRequest("WORD_BLOCKED", "La palabra \"" + w + "\" no está permitida.");
            }
        }
        if (title != null && moderation.isBlocked(title)) {
            throw ApiException.badRequest("TITLE_BLOCKED", "El título contiene palabras no permitidas.");
        }
        long seed = seeds.nextLong();
        // Con palabras propias se intenta colocarlas todas, sin el tope de la dificultad
        Difficulty d = difficulty;
        GeneratedGrid g = generator.generate(clean, d, seed, moderation.blockedWords(), clean.size());
        if (g.words().size() < MIN_PLACED_WORDS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WORDS_DO_NOT_FIT",
                    "No se pudieron acomodar suficientes palabras; usa palabras más cortas o una dificultad mayor.");
        }
        Puzzle p = new Puzzle(PuzzleKind.CUSTOM, null, d, seed, g, clock.instant());
        p.setTitle(title == null || title.isBlank() ? "Sopa personalizada" : title.trim());
        p.setCreatedBy(userId);
        p.setSource("CUSTOM", null, null);
        return PuzzleDto.of(puzzles.save(p)).withSkipped(g.skipped());
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public PuzzleDto get(UUID id) {
        return PuzzleDto.of(require(id));
    }

    @Transactional(readOnly = true)
    public Puzzle require(UUID id) {
        return puzzles.findWithWordsById(id)
                .map(this::initialize)
                .orElseThrow(() -> ApiException.notFound("PUZZLE_NOT_FOUND", "La sopa no existe."));
    }

    /** Carga lo perezoso dentro de la transacción para poder usarlo fuera. */
    private Puzzle initialize(Puzzle p) {
        p.getWords().size();
        if (p.getTopic() != null) {
            p.getTopic().getName();
        }
        return p;
    }
}
