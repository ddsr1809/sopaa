# Backend — Sopa de Letras

API REST que usan la app (Android / iOS) y el sitio web. Spring Boot 3 · Java 21 · PostgreSQL · Redis.

## Arrancar en local

Necesitas Java 21, Maven 3.9 y Docker.

```bash
# 1. Bases de datos (desde la raíz del repo)
docker compose up -d postgres redis

# 2. Backend (perfil dev: no exige verificar correo; los enlaces de correo salen en el log)
cd backend
mvn spring-boot:run
```

- API: http://localhost:8080/api/v1
- Swagger (documentación interactiva): http://localhost:8080/swagger-ui.html
- Contrato OpenAPI en JSON: http://localhost:8080/v3/api-docs
- Salud: http://localhost:8080/actuator/health

Para levantar todo en contenedores: `docker compose --profile app up --build`.

Desde el emulador de Android el backend local es `http://10.0.2.2:8080`; desde el simulador de iOS, `http://localhost:8080`.

## Pruebas

```bash
docker compose up -d postgres redis   # crea también la base sopa_test
cd backend && mvn verify
```

Las pruebas de integración usan PostgreSQL y Redis reales (base `sopa_test`, Redis db 1). En GitHub Actions se levantan como servicios automáticamente.

## Variables de entorno

| Variable | Para qué | Por defecto |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` en local, `prod` en testing y producción | `dev` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL | `localhost:5432/sopa`, `sopa`, `sopa` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis | `localhost`, `6379` |
| `APP_JWT_SECRET` | Firma de los tokens (≥ 32 caracteres). **Obligatorio en prod** | temporal en dev |
| `APP_GOOGLE_CLIENT_IDS` | Client IDs de Google de Android, iOS y web, separados por comas | vacío (Google desactivado) |
| `APP_WEB_BASE_URL` | Base de los enlaces de los correos (`/cuenta/verificar`, `/cuenta/restablecer`) | `http://localhost:3000` |
| `APP_MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `APP_MAIL_FROM` | Envío de correos por SMTP | apagado en dev |
| `APP_TIMEZONE` | Zona del "día" de la sopa diaria y de las semanas del ranking | `America/Mexico_City` |
| `APP_CORS_ORIGINS` | Orígenes web permitidos | `localhost:3000,5173` |
| `APP_REQUIRE_INTEGRITY` | Exigir Play Integrity / App Attest para el ranking | `false` |
| `SWAGGER_ENABLED` | Swagger en prod | `false` |

## Endpoints

Todas las rutas empiezan con `/api/v1`. 🔒 = requiere `Authorization: Bearer <accessToken>`.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/topics?category=` | Temas (favoritos y de tu región primero si hay sesión) |
| GET | `/topics/categories` | Categorías |
| GET | `/topics/{slug}` | Detalle de un tema |
| POST 🔒 | `/topics/suggestions` | Sugerir un tema |
| GET | `/puzzles/daily?date=&difficulty=` | Sopa del día (igual para todos) |
| GET | `/puzzles?topic=&difficulty=` | Sopa nueva de un tema |
| GET | `/puzzles/{id}` | Una sopa por id (enlaces compartidos, reanudar) |
| POST | `/puzzles/generate` | Sopa con palabras propias (no cuenta para ranking) |
| POST | `/sessions/start` | Iniciar partida (con sesión o con `X-Device-Id`) |
| POST | `/sessions/{id}/finish` | Terminar partida; el servidor calcula el puntaje |
| GET 🔒 | `/me/sessions` | Historial |
| GET | `/leaderboards?period=&scope=&id=` | Top 100 (WEEK/MONTH/ALL × CITY/STATE/COUNTRY/GLOBAL) |
| GET 🔒 | `/leaderboards/me?period=` | Mi posición en mis 4 zonas |
| GET | `/locations/search?q=&country=` | Buscar ciudad |
| POST | `/devices` | Registrar dispositivo y token de notificaciones |
| POST | `/auth/register` · `/auth/login` | Cuenta con correo |
| POST | `/auth/google` | Entrar o registrarse con Google |
| POST | `/auth/verify-email` · `/auth/resend-verification` | Confirmar correo |
| POST | `/auth/forgot` · `/auth/reset` | Recuperar contraseña |
| POST | `/auth/refresh` · `/auth/logout` | Renovar o cerrar sesión |
| GET · PATCH · DELETE 🔒 | `/me` | Perfil, editar alias/foto, borrar cuenta |
| PUT 🔒 | `/me/location` | Elegir o cambiar ciudad |
| PUT 🔒 | `/me/password` | Cambiar o agregar contraseña |
| GET · PUT · DELETE 🔒 | `/me/favorites[/{topicId}]` | Temas favoritos |
| POST 🔒 | `/admin/leaderboards/rebuild` | Reconstruir rankings (EDITOR / ADMIN) |

## Cómo debe usarlo la app

**Identificador del dispositivo.** Al instalar, la app genera un UUID y lo guarda. Lo manda como `X-Device-Id` en las partidas y como `deviceId` al iniciar sesión (así sus partidas anónimas pasan a su historial). Llama a `POST /devices` al abrir la app y cuando cambie el token de FCM/APNs.

**Sesión.**
1. `POST /auth/google` (con el ID token de Google) o `POST /auth/login` → `accessToken` (15 min) y `refreshToken` (30 días). Guárdalos cifrados (Android Keystore / iOS Keychain).
2. Si `user.profileComplete` es `false`, muestra el onboarding: `PATCH /me {alias}` y `PUT /me/location {locationId}` (con `GET /locations/search`).
3. Ante un `401` con `code` `TOKEN_EXPIRED` o `INVALID_TOKEN`, llama **una sola vez** a `POST /auth/refresh` y repite la petición. Cada refresh devuelve un refresh token nuevo y el anterior deja de servir; si se usa dos veces se cierra la sesión (`REFRESH_REUSED`). Serializa las renovaciones (un mutex en el interceptor).

**Partida.**
1. Obtén la sopa (`/puzzles/daily` o `/puzzles?topic=`). Trae las posiciones de las palabras para validar al instante y sin conexión.
2. `POST /sessions/start {puzzleId}` → `sessionId`, `ranked` y, si no cuenta, `unrankedReason` (para mostrar "regístrate para competir", "completa tu perfil", etc.).
3. Por cada palabra encontrada guarda `word`, `start`, `end` ([fila, columna]) y `msFromStart`.
4. `POST /sessions/{id}/finish {findings, hints, elapsedMs}` → puntaje oficial. Sin conexión, guárdalo y envíalo después (hasta 24 h).

**Errores.** Formato RFC 9457 (`application/problem+json`) con un `code` estable, por ejemplo:

```json
{ "status": 403, "title": "Forbidden", "detail": "Confirma tu correo para entrar.", "code": "EMAIL_NOT_VERIFIED" }
```

## Reglas del juego en el servidor

- **Puntaje:** 100 + 10 × letras por palabra; +200 por completar; +5 por cada segundo por debajo de 30 s × palabras; × 1 / 1.5 / 2 según dificultad; −50 por pista.
- **Ranking:** solo cuenta la primera partida completada de cada sopa, con cuenta verificada y perfil completo, máximo 50 al día. Las sopas personalizadas no cuentan.
- **Anti-trampa:** se rechazan posiciones falsas, tiempos imposibles (mínimo 1 s + 0.12 s por letra en cada palabra), tiempos mayores al real e intervalos robóticos. Esas partidas se guardan con `suspicious = true` y no suman.
- **Ciudad:** se elige una vez y se puede corregir una vez; después, un cambio cada 30 días. Un ranking de ciudad con menos de 10 jugadores responde `hidden: true` y la app muestra el del estado.
- **Borrar cuenta** (requisito de Google Play y App Store): se eliminan datos personales, las partidas quedan anónimas y el usuario sale de los rankings.

## Estructura

```
src/main/java/com/sopaletras/
├── puzzle/        Motor (engine/), sopas por tema, del día y personalizadas
├── topic/         Temas, favoritos y sugerencias
├── session/       Partidas, puntaje y verificación anti-trampa
├── leaderboard/   Rankings en Redis
├── auth/          Registro, login, Google, tokens, correos
├── user/          Perfil, alias, moderación, borrar cuenta
├── location/      Catálogo de ciudades
├── device/        Dispositivos y partidas anónimas
├── security/      JWT, permisos, límite de peticiones
├── config/        Propiedades, OpenAPI
└── common/        Errores, normalización de texto
src/main/resources/db/migration/   Esquema y datos iniciales (Flyway)
```

## Pendientes conocidos

- Catálogo de ciudades reducido (~100 de México y EE. UU.); para producción conviene importar GeoNames.
- La verificación de Play Integrity / App Attest es un punto de extensión (`IntegrityVerifier`), aún sin implementar.
- Panel de administración: solo existe la reconstrucción del ranking; faltan temas, moderación y revisión de partidas sospechosas.
- Ingesta de noticias y redes sociales (temas tipo NEWS / SOCIAL).
- El límite de peticiones es en memoria; con varias instancias del backend conviene moverlo a Redis.
- Si se ofrece "Continuar con Google" en iOS, la App Store pide también una opción equivalente como "Iniciar sesión con Apple".
