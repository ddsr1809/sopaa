package com.sopaletras.support;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopaletras.auth.GoogleTokenVerifier;
import com.sopaletras.auth.MailService;

/**
 * Base de las pruebas de integración: levanta la app completa contra PostgreSQL y Redis reales,
 * con el reloj controlable, el correo simulado y Google simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-26T18:00:00Z"));
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected MutableClock clock;

    @MockitoBean
    protected MailService mail;

    @MockitoBean
    protected GoogleTokenVerifier google;

    @BeforeEach
    void cleanState() {
        jdbc.execute("""
                truncate table play_sessions, puzzle_words, puzzles, refresh_tokens, email_tokens,
                  topic_favorites, topic_suggestions, devices, users cascade
                """);
        try (var conn = redis.getRequiredConnectionFactory().getConnection()) {
            conn.serverCommands().flushDb();
        }
        clock.set(Instant.parse("2026-10-26T18:00:00Z"));
    }

    // ------------------------------------------------------------------ peticiones

    protected JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req).andReturn();
        String body = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (res.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError("Se esperaba " + expectedStatus + " y llegó " + res.getResponse().getStatus()
                    + ": " + body);
        }
        return body.isEmpty() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    // ------------------------------------------------------------------ escenarios comunes

    /** Registra, confirma el correo e inicia sesión. Devuelve la respuesta de login. */
    protected JsonNode registerAndLogin(String email, String alias) throws Exception {
        call(json(post("/api/v1/auth/register"), Map.of("email", email, "password", "contraseña-segura",
                "alias", alias)), 201);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerification(eq(email), nullable(String.class), token.capture());
        call(json(post("/api/v1/auth/verify-email"), Map.of("token", token.getValue())), 200);
        return call(json(post("/api/v1/auth/login"), Map.of("email", email, "password", "contraseña-segura")), 200);
    }

    /** Usuario listo para el ranking: verificado, con alias y en San Luis Río Colorado. */
    protected String playerInSanLuis(String email, String alias) throws Exception {
        String token = registerAndLogin(email, alias).get("accessToken").asText();
        long cityId = cityId("san luis rio");
        call(json(auth(put("/api/v1/me/location"), token), Map.of("locationId", cityId)), 200);
        return token;
    }

    protected long cityId(String query) throws Exception {
        JsonNode list = call(get("/api/v1/locations/search").param("q", query), 200);
        return list.get(0).get("id").asLong();
    }

    protected JsonNode updateAlias(String token, String alias) throws Exception {
        return call(json(auth(patch("/api/v1/me"), token), Map.of("alias", alias)), 200);
    }
}
