package com.sopaletras.leaderboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sopaletras.common.ApiException;
import com.sopaletras.config.AppProperties;
import com.sopaletras.location.Location;
import com.sopaletras.location.LocationRepository;
import com.sopaletras.user.User;
import com.sopaletras.user.UserRepository;
import com.sopaletras.user.UserService;

/**
 * Rankings en Redis: un sorted set por (periodo × ámbito), por ejemplo {@code lb:w2026-44:state:MX-SON}.
 * <ul>
 *   <li>El puntaje entero vive en un hash paralelo ({@code …:pts}); el sorted set guarda
 *       puntos + una fracción que decrece con el tiempo, así en un empate gana quien llegó primero.</li>
 *   <li>{@code lbu:{userId}} recuerda en qué rankings aparece cada usuario, para poder sacarlo al borrar su cuenta.</li>
 *   <li>PostgreSQL es la fuente de verdad: {@link #rebuild()} reconstruye todo desde las partidas.</li>
 * </ul>
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private static final DefaultRedisScript<Long> ADD = new DefaultRedisScript<>("""
            local total = redis.call('HINCRBY', KEYS[2], ARGV[1], ARGV[2])
            redis.call('ZADD', KEYS[1], total + tonumber(ARGV[3]), ARGV[1])
            redis.call('SADD', KEYS[3], KEYS[1])
            if tonumber(ARGV[4]) > 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[4])
              redis.call('EXPIRE', KEYS[2], ARGV[4])
            end
            return total
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LocationRepository locations;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final Clock clock;

    public LeaderboardService(StringRedisTemplate redis, LocationRepository locations, UserRepository users,
                              JdbcTemplate jdbc, AppProperties props, Clock clock) {
        this.redis = redis;
        this.locations = locations;
        this.users = users;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ escritura

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPointsAwarded(PointsAwardedEvent e) {
        try {
            addPoints(e.userId(), e.locationId(), e.points(), e.at());
        } catch (RuntimeException ex) {
            // La partida ya está guardada como rankeada; un rebuild la recupera
            log.error("No se pudo actualizar el ranking de {}: {}", e.userId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserService.UserDeletedEvent e) {
        removeUser(e.userId());
    }

    public void addPoints(UUID userId, Long locationId, int points, Instant at) {
        if (points <= 0) {
            return;
        }
        Location loc = locationId == null ? null : locations.findById(locationId).orElse(null);
        LocalDate date = LocalDate.ofInstant(at, props.timezone());
        String tiebreak = Double.toString(1.0 - (at.getEpochSecond() / 60) / 1e8);
        String member = userId.toString();
        for (Period period : Period.values()) {
            String ttl = Long.toString(period.ttl.toSeconds());
            for (String scopeKey : scopeKeys(loc)) {
                String key = boardKey(period.key(date), scopeKey);
                redis.execute(ADD, List.of(key, key + ":pts", userKey(userId)),
                        member, Integer.toString(points), tiebreak, ttl);
            }
        }
    }

    public void removeUser(UUID userId) {
        String member = userId.toString();
        Set<String> keys = redis.opsForSet().members(userKey(userId));
        if (keys != null) {
            for (String key : keys) {
                redis.opsForZSet().remove(key, member);
                redis.opsForHash().delete(key + ":pts", member);
            }
        }
        redis.delete(userKey(userId));
    }

    /** Borra todos los rankings y los vuelve a calcular desde las partidas rankeadas en PostgreSQL. */
    public int rebuild() {
        List<String> toDelete = new ArrayList<>();
        try (Cursor<String> c = redis.scan(ScanOptions.scanOptions().match("lb*").count(1000).build())) {
            c.forEachRemaining(toDelete::add);
        }
        if (!toDelete.isEmpty()) {
            redis.delete(toDelete);
        }
        int[] count = {0};
        jdbc.query("""
                select user_id, location_id, score, finished_at from play_sessions
                where ranked and user_id is not null and finished_at is not null order by finished_at
                """, rs -> {
            addPoints(UUID.fromString(rs.getString("user_id")),
                    rs.getObject("location_id") == null ? null : rs.getLong("location_id"),
                    rs.getInt("score"), rs.getTimestamp("finished_at").toInstant());
            count[0]++;
        });
        return count[0];
    }

    // ------------------------------------------------------------------ lectura

    @Transactional(readOnly = true)
    public LeaderboardDtos.Board board(Period period, Scope scope, String scopeId, int limit, UUID viewer) {
        LocalDate today = LocalDate.now(clock.withZone(props.timezone()));
        Resolved r = resolve(scope, scopeId);
        String key = boardKey(period.key(today), r.scopeKey);
        Long card = redis.opsForZSet().zCard(key);
        long total = card == null ? 0 : card;

        if (scope == Scope.CITY && total < props.ranking().minCityPlayers()) {
            return new LeaderboardDtos.Board(period, period.key(today), period.start(today), period.end(today),
                    scope, r.id, r.name, total, true, Scope.STATE, List.of(), null);
        }

        int n = Math.max(1, Math.min(limit, 100));
        Set<String> top = redis.opsForZSet().reverseRange(key, 0, n - 1);
        List<String> ids = top == null ? List.of() : new ArrayList<>(top);
        Map<String, Long> points = points(key, ids);
        Map<UUID, User> people = new HashMap<>();
        users.findAllById(ids.stream().map(UUID::fromString).toList()).forEach(u -> people.put(u.getId(), u));

        List<LeaderboardDtos.Entry> entries = new ArrayList<>();
        long rank = 0;
        for (String id : ids) {
            rank++;
            User u = people.get(UUID.fromString(id));
            if (u == null) {
                continue; // cuenta borrada a medio camino
            }
            entries.add(new LeaderboardDtos.Entry(rank, u.getAlias(), u.getAvatarUrl(), points.getOrDefault(id, 0L),
                    viewer != null && viewer.equals(u.getId())));
        }
        LeaderboardDtos.Standing me = viewer == null ? null : standing(key, viewer, scope, r, false);
        return new LeaderboardDtos.Board(period, period.key(today), period.start(today), period.end(today),
                scope, r.id, r.name, total, false, null, entries, me);
    }

    /** Mi posición en mis 4 ámbitos (ciudad, estado, país y global) para un periodo. */
    @Transactional(readOnly = true)
    public LeaderboardDtos.MyStandings mine(UUID userId, Period period) {
        LocalDate today = LocalDate.now(clock.withZone(props.timezone()));
        Location loc = users.findById(userId).map(User::getLocation).orElse(null);
        List<LeaderboardDtos.Standing> list = new ArrayList<>();
        Map<Scope, Resolved> scopes = new LinkedHashMap<>();
        if (loc != null) {
            scopes.put(Scope.CITY, new Resolved(String.valueOf(loc.getId()), loc.getCity(), "city:" + loc.getId()));
            scopes.put(Scope.STATE, new Resolved(loc.getStateIso(), loc.getStateName(), "state:" + loc.getStateIso()));
            scopes.put(Scope.COUNTRY, new Resolved(loc.getCountryIso(), loc.getCountryName(),
                    "country:" + loc.getCountryIso()));
        }
        scopes.put(Scope.GLOBAL, new Resolved(null, "Global", "global"));
        for (var e : scopes.entrySet()) {
            String key = boardKey(period.key(today), e.getValue().scopeKey);
            boolean hidden = false;
            if (e.getKey() == Scope.CITY) {
                Long card = redis.opsForZSet().zCard(key);
                hidden = (card == null ? 0 : card) < props.ranking().minCityPlayers();
            }
            list.add(standing(key, userId, e.getKey(), e.getValue(), hidden));
        }
        return new LeaderboardDtos.MyStandings(period, period.key(today), period.start(today), period.end(today), list);
    }

    // ------------------------------------------------------------------ apoyo

    private LeaderboardDtos.Standing standing(String key, UUID userId, Scope scope, Resolved r, boolean hidden) {
        String member = userId.toString();
        Long idx = redis.opsForZSet().reverseRank(key, member);
        Long card = redis.opsForZSet().zCard(key);
        Object pts = redis.opsForHash().get(key + ":pts", member);
        long points = pts == null ? 0 : Long.parseLong(pts.toString());
        return new LeaderboardDtos.Standing(scope, r.id, r.name, idx == null ? null : idx + 1, points,
                card == null ? 0 : card, hidden);
    }

    private Map<String, Long> points(String key, List<String> ids) {
        Map<String, Long> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<Object> values = redis.opsForHash().multiGet(key + ":pts", new ArrayList<Object>(ids));
        for (int i = 0; i < ids.size(); i++) {
            Object v = values.get(i);
            out.put(ids.get(i), v == null ? 0L : Long.parseLong(v.toString()));
        }
        return out;
    }

    private record Resolved(String id, String name, String scopeKey) {
    }

    private Resolved resolve(Scope scope, String id) {
        return switch (scope) {
            case GLOBAL -> new Resolved(null, "Global", "global");
            case COUNTRY -> {
                String cc = required(id).toUpperCase();
                Location l = locations.findFirstByCountryIso(cc)
                        .orElseThrow(() -> ApiException.notFound("SCOPE_NOT_FOUND", "País no encontrado."));
                yield new Resolved(cc, l.getCountryName(), "country:" + cc);
            }
            case STATE -> {
                String st = required(id).toUpperCase();
                Location l = locations.findFirstByStateIso(st)
                        .orElseThrow(() -> ApiException.notFound("SCOPE_NOT_FOUND", "Estado no encontrado."));
                yield new Resolved(st, l.getStateName(), "state:" + st);
            }
            case CITY -> {
                long cityId;
                try {
                    cityId = Long.parseLong(required(id));
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("SCOPE_ID_INVALID", "El id de la ciudad debe ser numérico.");
                }
                Location l = locations.findById(cityId)
                        .orElseThrow(() -> ApiException.notFound("SCOPE_NOT_FOUND", "Ciudad no encontrada."));
                yield new Resolved(String.valueOf(cityId), l.getCity(), "city:" + cityId);
            }
        };
    }

    private static String required(String id) {
        if (id == null || id.isBlank()) {
            throw ApiException.badRequest("SCOPE_ID_REQUIRED", "Falta el parámetro id para ese ámbito.");
        }
        return id.trim();
    }

    private static List<String> scopeKeys(Location loc) {
        List<String> keys = new ArrayList<>(4);
        keys.add("global");
        if (loc != null) {
            keys.add("country:" + loc.getCountryIso());
            keys.add("state:" + loc.getStateIso());
            keys.add("city:" + loc.getId());
        }
        return keys;
    }

    static String boardKey(String periodKey, String scopeKey) {
        return "lb:" + periodKey + ":" + scopeKey;
    }

    static String userKey(UUID userId) {
        return "lbu:" + userId;
    }
}
