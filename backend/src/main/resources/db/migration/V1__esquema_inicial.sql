-- =====================================================================
-- Sopa de Letras: esquema inicial
-- PostgreSQL 14+
-- =====================================================================

-- ---------- Ubicaciones (país / estado / ciudad) ----------
CREATE TABLE locations (
    id            BIGSERIAL PRIMARY KEY,
    geoname_id    BIGINT UNIQUE,                 -- se llena al importar GeoNames
    country_iso   VARCHAR(2)   NOT NULL,         -- ISO 3166-1 (MX, US…)
    country_name  VARCHAR(80)  NOT NULL,
    state_iso     VARCHAR(10)  NOT NULL,         -- ISO 3166-2 (MX-SON…)
    state_name    VARCHAR(80)  NOT NULL,
    city          VARCHAR(120) NOT NULL,
    search_key    VARCHAR(200) NOT NULL,         -- ciudad + estado normalizados para buscar
    UNIQUE (state_iso, city)
);
CREATE INDEX idx_locations_search ON locations (search_key varchar_pattern_ops);

-- ---------- Usuarios ----------
CREATE TABLE users (
    id                   UUID PRIMARY KEY,
    email                VARCHAR(254) UNIQUE,     -- siempre en minúsculas
    email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash        VARCHAR(100),            -- vacío si solo entra con Google
    google_sub           VARCHAR(64)  UNIQUE,
    alias                VARCHAR(20),
    alias_key            VARCHAR(20)  UNIQUE,     -- alias normalizado, para unicidad sin mayúsculas/acentos
    avatar_url           VARCHAR(500),
    location_id          BIGINT REFERENCES locations (id),
    location_changed_at  TIMESTAMPTZ,
    role                 VARCHAR(10)  NOT NULL DEFAULT 'USER',     -- USER / EDITOR / ADMIN
    status               VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / SUSPENDED
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,    -- SHA-256 del token; el token nunca se guarda
    family       UUID        NOT NULL,           -- todos los tokens de una misma sesión
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN     NOT NULL DEFAULT FALSE,
    used_at      TIMESTAMPTZ,                    -- se marca al rotar; si se reutiliza → robo
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_family ON refresh_tokens (family);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

CREATE TABLE email_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,            -- VERIFY_EMAIL / RESET_PASSWORD
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Dispositivos (jugadores anónimos y notificaciones) ----------
CREATE TABLE devices (
    id            BIGSERIAL PRIMARY KEY,
    device_id     VARCHAR(100) NOT NULL UNIQUE,
    platform      VARCHAR(10)  NOT NULL,          -- ANDROID / IOS / WEB
    user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    push_token    VARCHAR(512),
    app_version   VARCHAR(40),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_devices_user ON devices (user_id);

-- ---------- Temas ----------
CREATE TABLE topics (
    id           BIGSERIAL PRIMARY KEY,
    slug         VARCHAR(80)  NOT NULL UNIQUE,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    type         VARCHAR(10)  NOT NULL,           -- NEWS / SOCIAL / CURATED / CUSTOM
    category     VARCHAR(40)  NOT NULL,
    region       VARCHAR(10),                     -- opcional: MX, MX-SON…
    language     VARCHAR(5)   NOT NULL DEFAULT 'es',
    icon         VARCHAR(60),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE topic_words (
    id        BIGSERIAL PRIMARY KEY,
    topic_id  BIGINT      NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    word      VARCHAR(40) NOT NULL,               -- como se muestra (con acentos)
    UNIQUE (topic_id, word)
);

CREATE TABLE topic_favorites (
    user_id     UUID   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic_id    BIGINT NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE topic_suggestions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    status       VARCHAR(12)  NOT NULL DEFAULT 'PENDING',   -- PENDING / APPROVED / REJECTED
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Sopas ----------
CREATE TABLE puzzles (
    id            UUID PRIMARY KEY,
    kind          VARCHAR(10)  NOT NULL,          -- DAILY / TOPIC / CUSTOM
    topic_id      BIGINT REFERENCES topics (id),
    difficulty    VARCHAR(10)  NOT NULL,          -- EASY / MEDIUM / HARD
    grid_rows     INT          NOT NULL,
    grid_cols     INT          NOT NULL,
    seed          BIGINT       NOT NULL,
    grid          JSONB        NOT NULL,          -- ["ABC…", "DEF…", …]
    title         VARCHAR(160),
    source_type   VARCHAR(10),
    source_title  VARCHAR(300),
    source_url    VARCHAR(1000),
    daily_date    DATE,
    created_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_puzzles_daily ON puzzles (daily_date, difficulty) WHERE kind = 'DAILY';
CREATE INDEX idx_puzzles_topic ON puzzles (topic_id, created_at DESC);

CREATE TABLE puzzle_words (
    id          BIGSERIAL PRIMARY KEY,
    puzzle_id   UUID        NOT NULL REFERENCES puzzles (id) ON DELETE CASCADE,
    position    INT         NOT NULL,
    word        VARCHAR(40) NOT NULL,             -- normalizada, como aparece en la cuadrícula
    display     VARCHAR(60) NOT NULL,             -- como se muestra en la lista
    start_row   INT         NOT NULL,
    start_col   INT         NOT NULL,
    direction   VARCHAR(12) NOT NULL
);
CREATE INDEX idx_puzzle_words_puzzle ON puzzle_words (puzzle_id);

-- ---------- Partidas ----------
CREATE TABLE play_sessions (
    id                UUID PRIMARY KEY,
    puzzle_id         UUID        NOT NULL REFERENCES puzzles (id),
    user_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    device_id         VARCHAR(100),
    location_id       BIGINT REFERENCES locations (id),          -- zona donde se ganaron los puntos
    status            VARCHAR(10) NOT NULL DEFAULT 'STARTED',   -- STARTED / FINISHED
    started_at        TIMESTAMPTZ NOT NULL,                     -- hora del servidor
    finished_at       TIMESTAMPTZ,
    claimed_ms        BIGINT,
    hints             INT         NOT NULL DEFAULT 0,
    findings          JSONB,
    words_found       INT         NOT NULL DEFAULT 0,
    completed         BOOLEAN     NOT NULL DEFAULT FALSE,
    score             INT         NOT NULL DEFAULT 0,           -- calculado por el servidor
    ranked            BOOLEAN     NOT NULL DEFAULT FALSE,
    suspicious        BOOLEAN     NOT NULL DEFAULT FALSE,
    suspicion_reason  VARCHAR(300),
    unranked_reason   VARCHAR(40)
);
CREATE INDEX idx_sessions_user ON play_sessions (user_id, started_at DESC);
CREATE INDEX idx_sessions_device ON play_sessions (device_id) WHERE user_id IS NULL;
CREATE INDEX idx_sessions_suspicious ON play_sessions (finished_at DESC) WHERE suspicious;
-- Solo la primera partida completada de cada sopa cuenta para el ranking
CREATE UNIQUE INDEX uq_sessions_ranked ON play_sessions (user_id, puzzle_id) WHERE ranked;

-- ---------- Moderación ----------
CREATE TABLE blocked_words (
    id        BIGSERIAL PRIMARY KEY,
    word      VARCHAR(60) NOT NULL UNIQUE,        -- normalizada
    category  VARCHAR(30) NOT NULL DEFAULT 'PROFANITY'
);
