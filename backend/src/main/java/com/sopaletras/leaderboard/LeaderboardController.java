package com.sopaletras.leaderboard;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Ranking")
public class LeaderboardController {

    private final LeaderboardService service;

    public LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/leaderboards")
    @Operation(summary = "Top de una zona y periodo",
            description = "Público. `scope`: GLOBAL, COUNTRY (id=MX), STATE (id=MX-SON) o CITY (id numérico de la ciudad). "
                    + "Una ciudad con menos de 10 jugadores responde `hidden: true` y `fallbackScope: STATE`. "
                    + "Con sesión iniciada incluye `me` con tu posición.")
    public LeaderboardDtos.Board board(
            @RequestParam(value = "period", defaultValue = "WEEK") Period period,
            @RequestParam(value = "scope", defaultValue = "GLOBAL") Scope scope,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.board(period, scope, id, limit, CurrentUser.id().orElse(null));
    }

    @GetMapping("/api/v1/leaderboards/me")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Mi posición en ciudad, estado, país y global")
    public LeaderboardDtos.MyStandings mine(@RequestParam(value = "period", defaultValue = "WEEK") Period period) {
        return service.mine(CurrentUser.require(), period);
    }

    @PostMapping("/api/v1/admin/leaderboards/rebuild")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Reconstruir rankings desde la base de datos (EDITOR / ADMIN)")
    public Map<String, Integer> rebuild() {
        return Map.of("sessions", service.rebuild());
    }
}
