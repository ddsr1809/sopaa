package com.sopaletras.topic;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/topics")
@Tag(name = "Temas")
public class TopicController {

    private final TopicService service;

    public TopicController(TopicService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista de temas",
            description = "Ordenados para el jugador: favoritos, de su región, más jugados de la semana y alfabético. "
                    + "Con sesión iniciada incluye `favorite`.")
    public List<TopicDto> list(@RequestParam(value = "category", required = false) String category) {
        return service.list(category, CurrentUser.id());
    }

    @GetMapping("/categories")
    @Operation(summary = "Categorías con al menos un tema activo")
    public List<String> categories() {
        return service.categories();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Detalle de un tema")
    public TopicDto get(@PathVariable("slug") String slug) {
        return service.get(slug, CurrentUser.id());
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Sugerir un tema nuevo", description = "Entra a la cola de revisión del panel. Máximo 5 por día.")
    public SuggestionResponse suggest(@Valid @RequestBody SuggestionRequest body) {
        TopicSuggestion s = service.suggest(CurrentUser.require(), body.name(), body.description());
        return new SuggestionResponse(s.getId(), s.getName(), s.getStatus(), s.getCreatedAt());
    }

    public record SuggestionRequest(
            @NotBlank @Size(min = 3, max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record SuggestionResponse(Long id, String name, String status, Instant createdAt) {
    }
}
