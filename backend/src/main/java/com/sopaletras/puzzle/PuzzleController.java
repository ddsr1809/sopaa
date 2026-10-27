package com.sopaletras.puzzle;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.puzzle.engine.Difficulty;
import com.sopaletras.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/puzzles")
@Tag(name = "Sopas")
public class PuzzleController {

    private final PuzzleService service;

    public PuzzleController(PuzzleService service) {
        this.service = service;
    }

    @GetMapping("/daily")
    @Operation(summary = "Sopa del día",
            description = "Igual para todos. `date` (AAAA-MM-DD) es opcional: por defecto hoy en la zona horaria del juego; "
                    + "se aceptan días anteriores hasta 30 días atrás.")
    public PuzzleDto daily(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "difficulty", defaultValue = "MEDIUM") Difficulty difficulty) {
        return service.daily(date, difficulty);
    }

    @GetMapping
    @Operation(summary = "Nueva sopa de un tema", description = "Cada llamada genera una sopa distinta con palabras del tema.")
    public PuzzleDto byTopic(@RequestParam("topic") String topic,
                             @RequestParam(value = "difficulty", defaultValue = "MEDIUM") Difficulty difficulty) {
        return service.forTopic(topic, difficulty);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una sopa por id", description = "Sirve para enlaces compartidos y para reanudar partidas.")
    public PuzzleDto get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar sopa con palabras propias",
            description = "Para el generador web y la app. No cuenta para el ranking. `skippedWords` lista las que no cupieron.")
    public PuzzleDto generate(@Valid @RequestBody GenerateRequest body) {
        Difficulty d = body.difficulty() == null ? Difficulty.MEDIUM : body.difficulty();
        return service.custom(body.words(), d, body.title(), CurrentUser.id().orElse(null));
    }

    public record GenerateRequest(
            @NotEmpty @Size(max = 50) List<String> words,
            Difficulty difficulty,
            @Size(max = 120) String title) {
    }
}
