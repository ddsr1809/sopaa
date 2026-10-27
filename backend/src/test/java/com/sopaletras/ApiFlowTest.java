package com.sopaletras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.sopaletras.auth.GoogleTokenVerifier.GoogleIdentity;
import com.sopaletras.support.IntegrationTest;

class ApiFlowTest extends IntegrationTest {

    // ------------------------------------------------------------------ temas y sopas

    @Test
    void temasYSopas() throws Exception {
        JsonNode topics = call(get("/api/v1/topics"), 200);
        assertThat(topics.size()).isEqualTo(13);
        assertThat(topics.get(0).get("wordCount").asInt()).isGreaterThanOrEqualTo(20);

        JsonNode daily1 = call(get("/api/v1/puzzles/daily"), 200);
        JsonNode daily2 = call(get("/api/v1/puzzles/daily"), 200);
        assertThat(daily1.get("id")).isEqualTo(daily2.get("id"));
        assertThat(daily1.get("kind").asText()).isEqualTo("DAILY");
        assertThat(daily1.get("dailyDate").asText()).isEqualTo("2026-10-26");
        call(get("/api/v1/puzzles/daily").param("date", "2026-10-27"), 400);

        JsonNode hard = call(get("/api/v1/puzzles").param("topic", "animales").param("difficulty", "HARD"), 200);
        assertThat(hard.get("grid").size()).isEqualTo(15);
        assertThat(hard.get("words").size()).isEqualTo(14);
        assertThat(hard.get("source").get("title").asText()).isEqualTo("Animales");
        JsonNode word = hard.get("words").get(0);
        assertThat(word.get("start").size()).isEqualTo(2);

        JsonNode same = call(get("/api/v1/puzzles/" + hard.get("id").asText()), 200);
        assertThat(same.get("grid")).isEqualTo(hard.get("grid"));

        call(get("/api/v1/puzzles").param("topic", "no-existe"), 404);

        JsonNode custom = call(json(post("/api/v1/puzzles/generate"), Map.of(
                "words", List.of("Hermosillo", "Nogales", "Guaymas", "Cananea", "Electroencefalografista"),
                "difficulty", "MEDIUM", "title", "Mi Sonora")), 201);
        assertThat(custom.get("words").size()).isEqualTo(4);
        assertThat(custom.get("skippedWords").get(0).asText()).isEqualTo("Electroencefalografista");

        JsonNode blocked = call(json(post("/api/v1/puzzles/generate"), Map.of(
                "words", List.of("Gato", "Pendejo", "Perro"))), 400);
        assertThat(blocked.get("code").asText()).isEqualTo("WORD_BLOCKED");
    }

    @Test
    void buscarCiudades() throws Exception {
        JsonNode res = call(get("/api/v1/locations/search").param("q", "san luis rio"), 200);
        assertThat(res.get(0).get("city").asText()).isEqualTo("San Luis Río Colorado");
        assertThat(res.get(0).get("stateIso").asText()).isEqualTo("MX-SON");
        JsonNode mx = call(get("/api/v1/locations/search").param("q", "san").param("country", "US"), 200);
        assertThat(mx).allSatisfy(n -> assertThat(n.get("countryIso").asText()).isEqualTo("US"));
    }

    // ------------------------------------------------------------------ cuentas

    @Test
    void registroVerificacionYPerfil() throws Exception {
        JsonNode reg = call(json(post("/api/v1/auth/register"),
                Map.of("email", "Ana@Ejemplo.com", "password", "contraseña-segura")), 201);
        assertThat(reg.get("verificationRequired").asBoolean()).isTrue();

        JsonNode notVerified = call(json(post("/api/v1/auth/login"),
                Map.of("email", "ana@ejemplo.com", "password", "contraseña-segura")), 403);
        assertThat(notVerified.get("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");

        JsonNode dup = call(json(post("/api/v1/auth/register"),
                Map.of("email", "ana@ejemplo.com", "password", "otra-contraseña")), 409);
        assertThat(dup.get("code").asText()).isEqualTo("EMAIL_TAKEN");

        call(json(post("/api/v1/auth/login"), Map.of("email", "nadie@ejemplo.com", "password", "loquesea123")), 401);

        JsonNode login = registerAndLogin("beto@ejemplo.com", "Beto");
        String token = login.get("accessToken").asText();
        assertThat(login.get("user").get("profileComplete").asBoolean()).isFalse();

        JsonNode taken = call(json(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me"),
                token), Map.of("alias", "BETO")), 200);
        assertThat(taken.get("alias").asText()).isEqualTo("BETO");

        JsonNode badAlias = call(json(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me"),
                token), Map.of("alias", "el_pendejo")), 400);
        assertThat(badAlias.get("code").asText()).isEqualTo("ALIAS_BLOCKED");

        long sanLuis = cityId("san luis rio");
        long hermosillo = cityId("hermosillo");
        long mexicali = cityId("mexicali");
        JsonNode me = call(json(auth(put("/api/v1/me/location"), token), Map.of("locationId", sanLuis)), 200);
        assertThat(me.get("profileComplete").asBoolean()).isTrue();
        call(json(auth(put("/api/v1/me/location"), token), Map.of("locationId", hermosillo)), 200);
        JsonNode tooSoon = call(json(auth(put("/api/v1/me/location"), token), Map.of("locationId", mexicali)), 409);
        assertThat(tooSoon.get("code").asText()).isEqualTo("LOCATION_CHANGE_TOO_SOON");
        clock.advance(Duration.ofDays(31));
        call(json(auth(put("/api/v1/me/location"), token), Map.of("locationId", mexicali)), 200);

        JsonNode favs = call(auth(get("/api/v1/me/favorites"), token), 200);
        assertThat(favs.size()).isZero();
        long topicId = call(get("/api/v1/topics/frutas"), 200).get("id").asLong();
        call(auth(put("/api/v1/me/favorites/" + topicId), token), 204);
        JsonNode topics = call(auth(get("/api/v1/topics"), token), 200);
        assertThat(topics.get(0).get("slug").asText()).isEqualTo("frutas");
        assertThat(topics.get(0).get("favorite").asBoolean()).isTrue();
    }

    @Test
    void sinTokenOTokenInvalido() throws Exception {
        assertThat(call(get("/api/v1/me"), 401).get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(call(auth(get("/api/v1/me"), "basura"), 401).get("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void refreshRotaYDetectaReuso() throws Exception {
        JsonNode login = registerAndLogin("carla@ejemplo.com", "Carla");
        String rt1 = login.get("refreshToken").asText();
        JsonNode r2 = call(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", rt1)), 200);
        String rt2 = r2.get("refreshToken").asText();
        assertThat(rt2).isNotEqualTo(rt1);

        JsonNode reused = call(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", rt1)), 401);
        assertThat(reused.get("code").asText()).isEqualTo("REFRESH_REUSED");
        // Toda la familia quedó revocada: el más nuevo tampoco sirve
        call(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", rt2)), 401);
    }

    @Test
    void googleCreaYLuegoReconoceLaCuenta() throws Exception {
        when(google.verify(anyString())).thenReturn(
                new GoogleIdentity("google-sub-1", "dana@gmail.com", true, "Dana", "https://foto/dana.png"));
        JsonNode first = call(json(post("/api/v1/auth/google"), Map.of("idToken", "x")), 200);
        assertThat(first.get("newUser").asBoolean()).isTrue();
        assertThat(first.get("user").get("emailVerified").asBoolean()).isTrue();
        JsonNode second = call(json(post("/api/v1/auth/google"), Map.of("idToken", "y")), 200);
        assertThat(second.get("newUser").asBoolean()).isFalse();
        assertThat(second.get("user").get("id")).isEqualTo(first.get("user").get("id"));
    }

    @Test
    void googleVinculaCuentaDeCorreoYDescartaContrasenaNoVerificada() throws Exception {
        call(json(post("/api/v1/auth/register"), Map.of("email", "eva@gmail.com", "password", "de-un-intruso")), 201);
        when(google.verify(anyString())).thenReturn(new GoogleIdentity("google-sub-2", "eva@gmail.com", true, "Eva", null));
        JsonNode res = call(json(post("/api/v1/auth/google"), Map.of("idToken", "x")), 200);
        assertThat(res.get("newUser").asBoolean()).isFalse();
        assertThat(res.get("user").get("hasPassword").asBoolean()).isFalse();
        assertThat(res.get("user").get("googleLinked").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ partidas y ranking

    @Test
    void partidaRankeadaAparecenEnElRanking() throws Exception {
        String token = playerInSanLuis("fer@ejemplo.com", "Fer");
        JsonNode puzzle = call(get("/api/v1/puzzles/daily"), 200);

        JsonNode start = call(json(auth(post("/api/v1/sessions/start"), token),
                Map.of("puzzleId", puzzle.get("id").asText())), 201);
        assertThat(start.get("ranked").asBoolean()).isTrue();

        clock.advance(Duration.ofSeconds(150));
        JsonNode fin = call(json(auth(post("/api/v1/sessions/" + start.get("sessionId").asText() + "/finish"), token),
                finishBody(puzzle, puzzle.get("words").size(), 145_000)), 200);
        assertThat(fin.get("ranked").asBoolean()).as(fin.toString()).isTrue();
        assertThat(fin.get("completed").asBoolean()).isTrue();
        int score = fin.get("score").asInt();
        assertThat(score).isPositive();

        JsonNode state = call(get("/api/v1/leaderboards").param("scope", "STATE").param("id", "MX-SON"), 200);
        assertThat(state.get("scopeName").asText()).isEqualTo("Sonora");
        assertThat(state.get("entries").get(0).get("alias").asText()).isEqualTo("Fer");
        assertThat(state.get("entries").get(0).get("points").asInt()).isEqualTo(score);

        JsonNode city = call(get("/api/v1/leaderboards").param("scope", "CITY").param("id", String.valueOf(cityId("san luis rio"))), 200);
        assertThat(city.get("hidden").asBoolean()).isTrue();
        assertThat(city.get("fallbackScope").asText()).isEqualTo("STATE");

        JsonNode mine = call(auth(get("/api/v1/leaderboards/me").param("period", "MONTH"), token), 200);
        assertThat(mine.get("standings")).hasSize(4);
        assertThat(mine.get("standings")).allSatisfy(s -> assertThat(s.get("rank").asInt()).isEqualTo(1));

        // La misma sopa otra vez ya no cuenta
        JsonNode again = call(json(auth(post("/api/v1/sessions/start"), token),
                Map.of("puzzleId", puzzle.get("id").asText())), 201);
        assertThat(again.get("unrankedReason").asText()).isEqualTo("ALREADY_PLAYED");

        JsonNode history = call(auth(get("/api/v1/me/sessions"), token), 200);
        assertThat(history.size()).isEqualTo(2);
    }

    @Test
    void soloCuentanPartidasCompletas() throws Exception {
        String a = playerInSanLuis("gaby@ejemplo.com", "Gaby");
        String b = playerInSanLuis("hugo@ejemplo.com", "Hugo");
        JsonNode p = call(get("/api/v1/puzzles").param("topic", "frutas").param("difficulty", "EASY"), 200);
        int n = p.get("words").size();

        String sa = call(json(auth(post("/api/v1/sessions/start"), a), Map.of("puzzleId", p.get("id").asText())), 201)
                .get("sessionId").asText();
        String sb = call(json(auth(post("/api/v1/sessions/start"), b), Map.of("puzzleId", p.get("id").asText())), 201)
                .get("sessionId").asText();
        clock.advance(Duration.ofSeconds(200));
        // Hugo encuentra todas; Gaby solo la mitad (partida incompleta: no cuenta)
        JsonNode fa = call(json(auth(post("/api/v1/sessions/" + sa + "/finish"), a), finishBody(p, n / 2, 190_000)), 200);
        assertThat(fa.get("unrankedReason").asText()).isEqualTo("INCOMPLETE");
        call(json(auth(post("/api/v1/sessions/" + sb + "/finish"), b), finishBody(p, n, 190_000)), 200);

        JsonNode global = call(auth(get("/api/v1/leaderboards"), a), 200);
        assertThat(global.get("entries")).hasSize(1);
        assertThat(global.get("entries").get(0).get("alias").asText()).isEqualTo("Hugo");
        assertThat(global.get("me").has("rank")).isFalse();
    }

    @Test
    void partidaAnonimaNoCuentaYEsDeSuDispositivo() throws Exception {
        JsonNode puzzle = call(get("/api/v1/puzzles").param("topic", "deportes").param("difficulty", "EASY"), 200);
        call(json(post("/api/v1/sessions/start"), Map.of("puzzleId", puzzle.get("id").asText())), 400);

        JsonNode start = call(json(post("/api/v1/sessions/start").header("X-Device-Id", "dispositivo-123"),
                Map.of("puzzleId", puzzle.get("id").asText())), 201);
        assertThat(start.get("ranked").asBoolean()).isFalse();
        assertThat(start.get("unrankedReason").asText()).isEqualTo("NOT_SIGNED_IN");
        String id = start.get("sessionId").asText();

        clock.advance(Duration.ofSeconds(120));
        call(json(post("/api/v1/sessions/" + id + "/finish").header("X-Device-Id", "otro-dispositivo"),
                finishBody(puzzle, 3, 60_000)), 403);
        JsonNode fin = call(json(post("/api/v1/sessions/" + id + "/finish").header("X-Device-Id", "dispositivo-123"),
                finishBody(puzzle, puzzle.get("words").size(), 110_000)), 200);
        assertThat(fin.get("ranked").asBoolean()).isFalse();
        assertThat(fin.get("score").asInt()).isPositive();
        call(json(post("/api/v1/sessions/" + id + "/finish").header("X-Device-Id", "dispositivo-123"),
                finishBody(puzzle, 3, 60_000)), 409);

        // Al registrarse, la partida pasa a su historial pero sin sumar al ranking
        call(json(post("/api/v1/auth/register"), Map.of("email", "ines@ejemplo.com", "password", "contraseña-segura")), 201);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendVerification(org.mockito.ArgumentMatchers.eq("ines@ejemplo.com"),
                org.mockito.ArgumentMatchers.nullable(String.class), captor.capture());
        call(json(post("/api/v1/auth/verify-email"), Map.of("token", captor.getValue())), 200);
        String token = call(json(post("/api/v1/auth/login"), Map.of("email", "ines@ejemplo.com",
                "password", "contraseña-segura", "deviceId", "dispositivo-123")), 200).get("accessToken").asText();
        JsonNode history = call(auth(get("/api/v1/me/sessions"), token), 200);
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("ranked").asBoolean()).isFalse();
    }

    @Test
    void trampaObviaNoSuma() throws Exception {
        String token = playerInSanLuis("juan@ejemplo.com", "Juan");
        JsonNode puzzle = call(get("/api/v1/puzzles").param("topic", "paises").param("difficulty", "MEDIUM"), 200);
        String id = call(json(auth(post("/api/v1/sessions/start"), token),
                Map.of("puzzleId", puzzle.get("id").asText())), 201).get("sessionId").asText();
        clock.advance(Duration.ofSeconds(3));
        JsonNode fin = call(json(auth(post("/api/v1/sessions/" + id + "/finish"), token),
                finishBody(puzzle, puzzle.get("words").size(), 2_500)), 200);
        assertThat(fin.get("ranked").asBoolean()).isFalse();
        assertThat(fin.get("unrankedReason").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("select suspicion_reason from play_sessions where id = ?::uuid", String.class, id))
                .isEqualTo("TOO_FAST");
    }

    @Test
    void borrarCuentaLaSacaDelRanking() throws Exception {
        String token = playerInSanLuis("karla@ejemplo.com", "Karla");
        JsonNode puzzle = call(get("/api/v1/puzzles/daily").param("difficulty", "EASY"), 200);
        String id = call(json(auth(post("/api/v1/sessions/start"), token),
                Map.of("puzzleId", puzzle.get("id").asText())), 201).get("sessionId").asText();
        clock.advance(Duration.ofSeconds(100));
        call(json(auth(post("/api/v1/sessions/" + id + "/finish"), token),
                finishBody(puzzle, puzzle.get("words").size(), 95_000)), 200);
        assertThat(call(get("/api/v1/leaderboards"), 200).get("entries")).hasSize(1);

        call(auth(delete("/api/v1/me"), token), 204);
        assertThat(call(get("/api/v1/leaderboards"), 200).get("entries")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from play_sessions where user_id is null", Integer.class))
                .isEqualTo(1);
        call(auth(get("/api/v1/me"), token), 401);
    }

    /** Arma un "finish" con las primeras n palabras, espaciadas de forma irregular como un humano. */
    private Map<String, Object> finishBody(JsonNode puzzle, int n, long elapsedMs) {
        List<Map<String, Object>> findings = new ArrayList<>();
        JsonNode words = puzzle.get("words");
        long step = elapsedMs / (n + 1);
        for (int i = 0; i < n; i++) {
            JsonNode w = words.get(i);
            Map<String, Object> f = new HashMap<>();
            f.put("word", w.get("display").asText());
            boolean reversed = i % 3 == 0;
            f.put("start", reversed ? w.get("end") : w.get("start"));
            f.put("end", reversed ? w.get("start") : w.get("end"));
            f.put("msFromStart", step * (i + 1) - (i % 2 == 0 ? step / 3 : 0));
            findings.add(f);
        }
        return Map.of("findings", findings, "hints", 0, "elapsedMs", elapsedMs);
    }
}
