package com.sopaletras.topic;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;
import com.sopaletras.location.Location;
import com.sopaletras.user.ModerationService;
import com.sopaletras.user.User;
import com.sopaletras.user.UserRepository;

@Service
public class TopicService {

    private static final int MAX_SUGGESTIONS_PER_DAY = 5;

    private final TopicRepository topics;
    private final TopicSuggestionRepository suggestions;
    private final UserRepository users;
    private final ModerationService moderation;
    private final Clock clock;

    public TopicService(TopicRepository topics, TopicSuggestionRepository suggestions, UserRepository users,
                        ModerationService moderation, Clock clock) {
        this.topics = topics;
        this.suggestions = suggestions;
        this.users = users;
        this.moderation = moderation;
        this.clock = clock;
    }

    /**
     * Temas activos en el orden que ve el jugador: favoritos, luego los de su región,
     * luego los más jugados de la semana, y por último alfabético.
     */
    @Transactional(readOnly = true)
    public List<TopicDto> list(String category, Optional<UUID> userId) {
        Map<Long, Long> words = toMap(topics.countWords());
        Map<Long, Long> plays = toMap(topics.countPlaysSince(clock.instant().minus(Duration.ofDays(7))));
        Set<Long> favorites = userId.map(id -> (Set<Long>) new HashSet<>(topics.findFavoriteIds(id))).orElse(Set.of());
        Location loc = userId.flatMap(users::findById).map(User::getLocation).orElse(null);

        return topics.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> category == null || category.isBlank() || t.getCategory().equalsIgnoreCase(category))
                .sorted(Comparator
                        .comparing((Topic t) -> !favorites.contains(t.getId()))
                        .thenComparing(t -> !matchesRegion(t, loc))
                        .thenComparing(t -> -plays.getOrDefault(t.getId(), 0L))
                        .thenComparing(Topic::getName))
                .map(t -> TopicDto.of(t, words.getOrDefault(t.getId(), 0L), plays.getOrDefault(t.getId(), 0L),
                        favorites.contains(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return topics.findByActiveTrueOrderByNameAsc().stream().map(Topic::getCategory).distinct().sorted().toList();
    }

    @Transactional(readOnly = true)
    public TopicDto get(String slug, Optional<UUID> userId) {
        Topic t = requireActive(slug);
        long words = topics.findWords(t.getId()).size();
        boolean fav = userId.map(id -> topics.findFavoriteIds(id).contains(t.getId())).orElse(false);
        return TopicDto.of(t, words, 0, fav);
    }

    public Topic requireActive(String slug) {
        return topics.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "El tema no existe."));
    }

    @Transactional(readOnly = true)
    public List<TopicDto> favorites(UUID userId) {
        Set<Long> ids = new HashSet<>(topics.findFavoriteIds(userId));
        Map<Long, Long> words = toMap(topics.countWords());
        return topics.findAllById(ids).stream()
                .filter(Topic::isActive)
                .sorted(Comparator.comparing(Topic::getName))
                .map(t -> TopicDto.of(t, words.getOrDefault(t.getId(), 0L), 0, true))
                .toList();
    }

    @Transactional
    public void addFavorite(UUID userId, long topicId) {
        Topic t = topics.findById(topicId).filter(Topic::isActive)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "El tema no existe."));
        topics.addFavorite(userId, t.getId());
    }

    @Transactional
    public void removeFavorite(UUID userId, long topicId) {
        topics.removeFavorite(userId, topicId);
    }

    @Transactional
    public TopicSuggestion suggest(UUID userId, String name, String description) {
        Instant now = clock.instant();
        if (suggestions.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofDays(1))) >= MAX_SUGGESTIONS_PER_DAY) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "SUGGESTION_LIMIT",
                    "Ya enviaste varias sugerencias hoy. Inténtalo mañana.");
        }
        if (moderation.isBlocked(name) || (description != null && moderation.isBlocked(description))) {
            throw ApiException.badRequest("CONTENT_BLOCKED", "La sugerencia contiene palabras no permitidas.");
        }
        return suggestions.save(new TopicSuggestion(userId, name.trim(),
                description == null ? null : description.trim(), now));
    }

    private static boolean matchesRegion(Topic t, Location loc) {
        if (t.getRegion() == null || loc == null) {
            return false;
        }
        return t.getRegion().equals(loc.getStateIso()) || t.getRegion().equals(loc.getCountryIso());
    }

    private static Map<Long, Long> toMap(List<TopicRepository.TopicCount> counts) {
        return counts.stream().collect(Collectors.toMap(TopicRepository.TopicCount::getTopicId,
                TopicRepository.TopicCount::getTotal));
    }
}
