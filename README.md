# Sopa de Letras

Juego de sopa de letras conectado con noticias y temas, para Android, iOS y web.

| Carpeta | Contenido |
|---|---|
| [`backend/`](backend/README.md) | API REST (Spring Boot 3, Java 21, PostgreSQL, Redis) |
| `android/` | App Android (pendiente) |
| `ios/` | App iOS (pendiente) |
| `web/` | Sitio: presentación, generador y panel (pendiente) |

## Ramas

| Rama | Uso |
|---|---|
| `development` | Trabajo diario. Las funciones nuevas entran aquí (directo o por pull request). |
| `testing` | Lo que se va a probar en el ambiente de pruebas. Se actualiza con un merge de `development`. |
| `main` | Producción. Solo recibe merges de `testing` ya probados. |

```bash
# Promover development → testing
git checkout testing && git merge --ff-only development && git push

# Promover testing → main
git checkout main && git merge --ff-only testing && git push
```

GitHub Actions compila y corre las pruebas del backend en cada push y pull request a las tres ramas.

## Arranque rápido

```bash
docker compose up -d postgres redis
cd backend && mvn spring-boot:run
# Swagger: http://localhost:8080/swagger-ui.html
```
