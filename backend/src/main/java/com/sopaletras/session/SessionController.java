package com.sopaletras.session;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.security.CurrentUser;
import com.sopaletras.session.SessionDtos.FinishRequest;
import com.sopaletras.session.SessionDtos.FinishResponse;
import com.sopaletras.session.SessionDtos.SessionSummary;
import com.sopaletras.session.SessionDtos.StartRequest;
import com.sopaletras.session.SessionDtos.StartResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Partidas")
public class SessionController {

    private static final String DEVICE_HEADER = "X-Device-Id";

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/sessions/start")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Iniciar partida",
            description = "El servidor anota la hora de inicio. Con sesión iniciada puede contar para el ranking; "
                    + "sin sesión, manda `X-Device-Id`. `ranked=false` trae `unrankedReason` "
                    + "(NOT_SIGNED_IN, EMAIL_NOT_VERIFIED, PROFILE_INCOMPLETE, CUSTOM_PUZZLE, ALREADY_PLAYED, DAILY_LIMIT).")
    public StartResponse start(@Valid @RequestBody StartRequest body,
                               @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId) {
        return service.start(body.puzzleId(), CurrentUser.id().orElse(null), deviceId);
    }

    @PostMapping("/api/v1/sessions/{id}/finish")
    @Operation(summary = "Terminar partida",
            description = "Manda cada palabra encontrada con su inicio, fin ([fila, columna]) y el momento (ms desde el inicio). "
                    + "El servidor recalcula el puntaje. Se puede enviar después sin conexión, hasta 24 h tras el inicio "
                    + "(después ya no cuenta: LATE). Otros motivos: INCOMPLETE, INTEGRITY, UNDER_REVIEW.")
    public FinishResponse finish(@PathVariable("id") UUID id, @Valid @RequestBody FinishRequest body,
                                 @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId) {
        return service.finish(id, body, CurrentUser.id().orElse(null), deviceId);
    }

    @GetMapping("/api/v1/me/sessions")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Mi historial de partidas")
    public List<SessionSummary> history(@RequestParam(value = "page", defaultValue = "0") int page,
                                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return service.history(CurrentUser.require(), page, size);
    }
}
