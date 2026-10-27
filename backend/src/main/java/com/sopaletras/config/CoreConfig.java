package com.sopaletras.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class CoreConfig {

    /** Reloj inyectable: las pruebas pueden fijar la hora. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sopa de Letras API")
                        .version("v1")
                        .description("""
                                API que usan la app (Android / iOS) y el sitio web.

                                **Autenticación:** `Authorization: Bearer <accessToken>` (dura 15 min). \
                                Al recibir 401 con `code = TOKEN_EXPIRED` o `INVALID_TOKEN`, renueva con \
                                `POST /api/v1/auth/refresh` **una sola vez** y repite la petición.

                                **Jugadores anónimos:** manda `X-Device-Id` (un UUID generado al instalar la app) \
                                en las peticiones de partidas.

                                **Errores:** formato RFC 9457 con un campo `code` estable."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
