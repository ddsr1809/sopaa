package com.sopaletras.device;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sopaletras.common.ApiException;

@Service
public class DeviceService {

    /** Un UUID u otro identificador opaco generado por la app al instalarse. */
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final DeviceRepository devices;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, JdbcTemplate jdbc, Clock clock) {
        this.devices = devices;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static String validate(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            throw ApiException.badRequest("INVALID_DEVICE_ID",
                    "X-Device-Id debe tener de 8 a 100 caracteres (letras, números, - o _).");
        }
        return deviceId;
    }

    @Transactional
    public void register(String deviceId, Platform platform, String pushToken, String appVersion, UUID userId) {
        validate(deviceId);
        Device d = devices.findByDeviceId(deviceId).orElseGet(() -> new Device(deviceId, platform, clock.instant()));
        d.update(platform, pushToken, appVersion, userId, clock.instant());
        devices.save(d);
    }

    /**
     * Al iniciar sesión: el dispositivo queda ligado a la cuenta y sus partidas anónimas pasan
     * al historial del usuario (sin sumar al ranking).
     */
    @Transactional
    public int claimAnonymousPlays(String deviceId, UUID userId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            return 0;
        }
        devices.findByDeviceId(deviceId).ifPresent(d -> d.setUserId(userId));
        return jdbc.update("update play_sessions set user_id = ? where device_id = ? and user_id is null",
                userId, deviceId);
    }
}
