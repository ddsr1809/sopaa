package com.sopaletras.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "push_token")
    private String pushToken;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Device() {
    }

    public Device(String deviceId, Platform platform, Instant now) {
        this.deviceId = deviceId;
        this.platform = platform;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public void update(Platform platform, String pushToken, String appVersion, UUID userId, Instant now) {
        this.platform = platform;
        if (pushToken != null) {
            this.pushToken = pushToken;
        }
        if (appVersion != null) {
            this.appVersion = appVersion;
        }
        if (userId != null) {
            this.userId = userId;
        }
        this.lastSeenAt = now;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getPushToken() {
        return pushToken;
    }
}
