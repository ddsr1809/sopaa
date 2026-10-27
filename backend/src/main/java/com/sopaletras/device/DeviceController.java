package com.sopaletras.device;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sopaletras.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Dispositivos")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registrar o actualizar el dispositivo",
            description = "Llamar al abrir la app y cuando cambie el token de notificaciones (FCM / APNs). "
                    + "Si hay sesión iniciada, el dispositivo queda ligado a la cuenta.")
    public void register(@Valid @RequestBody DeviceRequest body) {
        service.register(body.deviceId(), body.platform(), body.pushToken(), body.appVersion(),
                CurrentUser.id().orElse(null));
    }

    public record DeviceRequest(
            @NotBlank String deviceId,
            @NotNull Platform platform,
            @Size(max = 512) String pushToken,
            @Size(max = 40) String appVersion) {
    }
}
