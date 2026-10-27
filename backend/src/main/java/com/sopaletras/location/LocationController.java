package com.sopaletras.location;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.common.ApiException;
import com.sopaletras.common.TextNormalizer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Ubicaciones")
public class LocationController {

    private final LocationRepository locations;

    public LocationController(LocationRepository locations) {
        this.locations = locations;
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar ciudad", description = "Busca por nombre de ciudad o estado, sin importar acentos. Mínimo 2 letras.")
    public List<LocationDto> search(@RequestParam("q") String q,
                                    @RequestParam(value = "country", required = false) String country,
                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
        String key = TextNormalizer.forSearch(q);
        if (key.length() < 2) {
            return List.of();
        }
        String cc = country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
        int size = Math.max(1, Math.min(limit, 50));
        return locations.search(key, cc, PageRequest.of(0, size)).stream().map(LocationDto::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una ciudad")
    public LocationDto get(@PathVariable("id") long id) {
        return locations.findById(id).map(LocationDto::of)
                .orElseThrow(() -> ApiException.notFound("LOCATION_NOT_FOUND", "La ciudad no existe."));
    }
}
