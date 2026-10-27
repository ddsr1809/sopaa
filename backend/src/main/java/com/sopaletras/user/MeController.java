package com.sopaletras.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.security.CurrentUser;
import com.sopaletras.topic.TopicDto;
import com.sopaletras.topic.TopicService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Mi cuenta")
@SecurityRequirement(name = "bearer")
public class MeController {

    private final UserService users;
    private final TopicService topics;

    public MeController(UserService users, TopicService topics) {
        this.users = users;
        this.topics = topics;
    }

    @GetMapping
    @Operation(summary = "Mi perfil")
    public MeDto me() {
        return users.me(CurrentUser.require());
    }

    @PatchMapping
    @Operation(summary = "Editar alias o foto", description = "Solo se cambian los campos enviados. Errores: ALIAS_TAKEN, ALIAS_INVALID, ALIAS_BLOCKED.")
    public MeDto update(@Valid @RequestBody UpdateProfileRequest body) {
        return users.updateProfile(CurrentUser.require(), body.alias(), body.avatarUrl());
    }

    @PutMapping("/location")
    @Operation(summary = "Elegir o cambiar mi ciudad",
            description = "La primera elección y un primer cambio son libres; después, una vez cada 30 días (LOCATION_CHANGE_TOO_SOON).")
    public MeDto location(@Valid @RequestBody LocationRequest body) {
        return users.changeLocation(CurrentUser.require(), body.locationId());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar o agregar contraseña",
            description = "Si la cuenta ya tiene contraseña, se pide la actual. Las cuentas de Google pueden agregar una.")
    public void password(@Valid @RequestBody PasswordRequest body) {
        users.setPassword(CurrentUser.require(), body.currentPassword(), body.newPassword());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar mi cuenta",
            description = "Elimina datos personales, deja las partidas anónimas y saca al usuario de los rankings. No se puede deshacer.")
    public void delete() {
        users.delete(CurrentUser.require());
    }

    @GetMapping("/favorites")
    @Operation(summary = "Mis temas favoritos")
    public List<TopicDto> favorites() {
        return topics.favorites(CurrentUser.require());
    }

    @PutMapping("/favorites/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar tema como favorito")
    public void addFavorite(@PathVariable("topicId") long topicId) {
        topics.addFavorite(CurrentUser.require(), topicId);
    }

    @DeleteMapping("/favorites/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar tema de favoritos")
    public void removeFavorite(@PathVariable("topicId") long topicId) {
        topics.removeFavorite(CurrentUser.require(), topicId);
    }

    public record UpdateProfileRequest(@Size(max = 20) String alias, @Size(max = 500) String avatarUrl) {
    }

    public record LocationRequest(@NotNull Long locationId) {
    }

    public record PasswordRequest(
            String currentPassword,
            @NotNull @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String newPassword) {
    }
}
