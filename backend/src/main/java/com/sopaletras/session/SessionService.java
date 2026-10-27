package com.sopaletras.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;
import com.sopaletras.config.AppProperties;
import com.sopaletras.device.DeviceService;
import com.sopaletras.leaderboard.PointsAwardedEvent;
import com.sopaletras.puzzle.Puzzle;
import com.sopaletras.puzzle.PuzzleKind;
import com.sopaletras.puzzle.PuzzleRepository;
import com.sopaletras.puzzle.PuzzleService;
import com.sopaletras.session.SessionDtos.FinishRequest;
import com.sopaletras.session.SessionDtos.FinishResponse;
import com.sopaletras.session.SessionDtos.SessionSummary;
import com.sopaletras.session.SessionDtos.StartResponse;
import com.sopaletras.user.User;
import com.sopaletras.user.UserRepository;

/**
 * Ciclo de una partida: start (el servidor anota la hora) → finish (la app manda lo que encontró;
 * el servidor valida, calcula el puntaje y decide si cuenta para el ranking).
 */
@Service
public class SessionService {

    /** Motivos por los que una partida no suma al ranking (la app los puede traducir a mensajes). */
    public static final String NOT_SIGNED_IN = "NOT_SIGNED_IN";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
    public static final String CUSTOM_PUZZLE = "CUSTOM_PUZZLE";
    public static final String ALREADY_PLAYED = "ALREADY_PLAYED";
    public static final String DAILY_LIMIT = "DAILY_LIMIT";
    public static final String INCOMPLETE = "INCOMPLETE";
    public static final String LATE = "LATE";
    public static final String INTEGRITY = "INTEGRITY";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";

    private final PlaySessionRepository sessions;
    private final PuzzleService puzzleService;
    private final PuzzleRepository puzzles;
    private final UserRepository users;
    private final IntegrityVerifier integrity;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final SessionVerifier verifier;
    private final Clock clock;

    public SessionService(PlaySessionRepository sessions, PuzzleService puzzleService, PuzzleRepository puzzles,
                          UserRepository users, IntegrityVerifier integrity, ApplicationEventPublisher events,
                          AppProperties props, Clock clock) {
        this.sessions = sessions;
        this.puzzleService = puzzleService;
        this.puzzles = puzzles;
        this.users = users;
        this.integrity = integrity;
        this.events = events;
        this.props = props;
        this.verifier = new SessionVerifier(props.ranking());
        this.clock = clock;
    }

    @Transactional
    public StartResponse start(UUID puzzleId, UUID userId, String deviceId) {
        if (userId == null && deviceId == null) {
            throw ApiException.badRequest("DEVICE_ID_REQUIRED", "Sin sesión iniciada hace falta el encabezado X-Device-Id.");
        }
        if (deviceId != null) {
            DeviceService.validate(deviceId);
        }
        Puzzle puzzle = puzzleService.require(puzzleId);
        User user = userId == null ? null : users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "La cuenta ya no existe."));
        if (user != null && !user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Tu cuenta está suspendida.");
        }
        Instant now = clock.instant();
        String reason = eligibility(user, puzzle, now);
        PlaySession s = sessions.save(new PlaySession(puzzle.getId(), userId, deviceId, now, reason));
        return new StartResponse(s.getId(), puzzle.getId(), now, now.plus(props.ranking().offlineFinishWindow()),
                reason == null, reason);
    }

    @Transactional
    public FinishResponse finish(UUID sessionId, FinishRequest req, UUID userId, String deviceId) {
        PlaySession s = sessions.findForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "La partida no existe."));
        boolean owner = (userId != null && userId.equals(s.getUserId()))
                || (deviceId != null && deviceId.equals(s.getDeviceId()));
        if (!owner) {
            throw ApiException.forbidden("NOT_YOUR_SESSION", "Esta partida no es tuya.");
        }
        if (s.getStatus() == PlaySession.Status.FINISHED) {
            throw ApiException.conflict("SESSION_ALREADY_FINISHED", "Esta partida ya se terminó.");
        }

        Puzzle puzzle = puzzleService.require(s.getPuzzleId());
        Instant now = clock.instant();
        Duration serverElapsed = Duration.between(s.getStartedAt(), now);
        SessionVerifier.Result check = verifier.verify(puzzle.placedWords(), req.findings(), req.elapsedMs(), serverElapsed);
        int total = puzzle.placedWords().size();
        boolean completed = check.foundWords().size() == total;
        ScoreCalculator.Breakdown score = ScoreCalculator.calculate(check.foundWords(), total, req.elapsedMs(),
                req.hints(), puzzle.getDifficulty());

        s.finish(now, req.elapsedMs(), req.hints(), check.accepted(), check.foundWords().size(), completed, score.total());

        // ¿Cuenta para el ranking? Una partida iniciada sin sesión nunca cuenta.
        String reason = NOT_SIGNED_IN.equals(s.getUnrankedReason()) || s.getUserId() == null ? NOT_SIGNED_IN : null;
        User user = null;
        if (reason == null) {
            user = users.findById(s.getUserId()).orElse(null);
            reason = user == null ? NOT_SIGNED_IN : eligibility(user, puzzle, now);
        }
        if (reason == null && !completed) {
            reason = INCOMPLETE;
        }
        if (reason == null && serverElapsed.compareTo(props.ranking().offlineFinishWindow()) > 0) {
            reason = LATE;
        }
        if (reason == null && props.ranking().requireIntegrity()
                && integrity.verify(req.integrityToken(), s.getDeviceId()) != IntegrityVerifier.Verdict.PASS) {
            reason = INTEGRITY;
        }
        if (check.suspicious()) {
            s.markSuspicious(check.suspicionReason());
            if (reason == null) {
                reason = UNDER_REVIEW;
            }
        }

        if (reason == null) {
            Long locationId = user.getLocation() == null ? null : user.getLocation().getId();
            s.markRanked(locationId);
            sessions.saveAndFlush(s); // el índice único evita contar dos veces la misma sopa
            events.publishEvent(new PointsAwardedEvent(user.getId(), locationId, score.total(), now));
        } else {
            s.markUnranked(reason);
        }
        return new FinishResponse(s.getId(), score.total(), score, check.foundWords().size(), total, completed,
                s.isRanked(), s.isRanked() ? null : reason);
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> history(UUID userId, int page, int size) {
        Page<PlaySession> rows = sessions.findByUserIdOrderByStartedAtDesc(userId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))));
        Map<UUID, Puzzle> byId = new HashMap<>();
        puzzles.findAllById(rows.stream().map(PlaySession::getPuzzleId).distinct().toList())
                .forEach(p -> byId.put(p.getId(), p));
        return rows.stream().map(s -> {
            Puzzle p = byId.get(s.getPuzzleId());
            return new SessionSummary(s.getId(), s.getPuzzleId(), p == null ? null : p.getTitle(),
                    p == null ? null : p.getKind(), p == null ? null : p.getDifficulty(), s.getStartedAt(),
                    s.getFinishedAt(), s.getScore(), s.getWordsFound(), s.isCompleted(), s.isRanked());
        }).toList();
    }

    /** null si puede contar para el ranking; si no, el motivo. */
    private String eligibility(User user, Puzzle puzzle, Instant at) {
        if (user == null) {
            return NOT_SIGNED_IN;
        }
        if (props.auth().requireEmailVerification() && !user.isEmailVerified()) {
            return EMAIL_NOT_VERIFIED;
        }
        if (!user.isProfileComplete()) {
            return PROFILE_INCOMPLETE;
        }
        if (puzzle.getKind() == PuzzleKind.CUSTOM) {
            return CUSTOM_PUZZLE;
        }
        if (sessions.existsByUserIdAndPuzzleIdAndRankedTrue(user.getId(), puzzle.getId())) {
            return ALREADY_PLAYED;
        }
        long today = sessions.countByUserIdAndRankedTrueAndFinishedAtAfter(user.getId(), at.minus(Duration.ofDays(1)));
        if (today >= props.ranking().maxRankedPerDay()) {
            return DAILY_LIMIT;
        }
        return null;
    }
}
