# ktor_phrases

[![CI](https://github.com/Giancarmine/ktor_phrases/actions/workflows/ci.yml/badge.svg)](https://github.com/Giancarmine/ktor_phrases/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A small emoji/phrase association API built with [Ktor](https://ktor.io/). You POST an emoji
with a phrase, and the service stores it in memory, exposes it as JSON, and renders it on a
server-side FreeMarker page.

Originally written in 2019 following the Ray Wenderlich
["Server-side Kotlin with Ktor"](https://www.raywenderlich.com/2885892-server-side-kotlin-with-ktor)
tutorial. This repository has since been modernized: Ktor 1.2 → 3.6, Kotlin 1.3 → 2.4,
Gradle 4.10 → 9.8, Moshi → kotlinx.serialization, plus tests and CI.

## Stack

| Component | Version |
|---|---|
| Kotlin | 2.4.21 |
| Ktor | 3.6.0 |
| Gradle | 9.8.2 |
| JDK | 21 (toolchain) |
| Serialization | kotlinx.serialization |
| Templating | FreeMarker |
| Logging | Logback 1.5.38 |
| Tests | JUnit 5 + Ktor `testApplication` |

## Status and breaking changes

- **Breaking:** the collection endpoint moved from `POST /api/v1/phrase` to
  `POST /api/v1/phrases`. The old singular path is no longer served.
- The Heroku `Procfile`, the dead `ktor-phrases.herokuapp.com` link, and the Shadow plugin
  configuration were removed. Deployment now uses the fat JAR produced by the Ktor Gradle plugin.
- Data is still **in memory** and is lost on restart. There is no database or authentication.
- Packaging changed from a hand-written `shadowJar` block to `io.ktor.plugin` (`buildFatJar`).

## Architecture

```
HTTP client
    │
    ▼
Netty engine (io.ktor.server.netty.EngineMain)
    │  reads src/main/resources/application.conf
    ▼
Application.module(repository = InMemoryRepository())
    ├── DefaultHeaders
    ├── StatusPages ............ maps exceptions to JSON errors
    ├── ContentNegotiation ..... kotlinx.serialization JSON
    ├── FreeMarker ............. templates/*.ftl
    └── routing
         ├── GET  /              plain-text endpoint index
         ├── GET  /about         project info
         ├── GET  /phrases       HTML list (FreeMarker)
         └── /api/v1/phrases     JSON API
                  │
                  ▼
             Repository ◄── InMemoryRepository (Mutex + LinkedHashMap)
```

`Application.module` takes the repository as a parameter (defaulting to `InMemoryRepository`),
so tests or future storage backends can inject their own implementation. All repository
operations run under a `Mutex`, which makes the in-memory store safe for concurrent requests.

## Project layout

```
src/main/kotlin/com/ges/
├── Application.kt              entry point, plugins, module wiring
├── api/Phrase.kt               /api/v1/phrases routes and validation
├── model/
│   ├── EmojiPhrase.kt          stored entity (id, emoji, phrase)
│   ├── PhraseRequest.kt        POST payload
│   └── Errors.kt               ErrorResponse + domain exceptions
├── repository/
│   ├── Repository.kt           storage contract
│   └── InMemoryRepository.kt   thread-safe in-memory implementation
└── webapp/
    ├── Home.kt                 GET /
    ├── About.kt                GET /about
    └── Phrases.kt              GET /phrases (FreeMarker)
src/main/resources/
├── application.conf            port and module configuration
├── logback.xml
└── templates/phrases.ftl       HTML view
src/test/kotlin/com/ges/
└── ApplicationTest.kt          8 integration tests via testApplication
```

## API

| Method | Path | Success | Notes |
|---|---|---|---|
| `GET` | `/` | `200` | Plain-text list of endpoints |
| `GET` | `/about` | `200` | Project and source link |
| `GET` | `/phrases` | `200` | HTML page rendered with FreeMarker |
| `GET` | `/api/v1/phrases` | `200` | All phrases as a JSON array |
| `POST` | `/api/v1/phrases` | `201` | Creates a phrase and returns a `Location` header. Returns `200` if the exact pair already exists |
| `GET` | `/api/v1/phrases/{id}` | `200` | Single phrase |
| `DELETE` | `/api/v1/phrases/{id}` | `204` | Removes a phrase |

A phrase is:

```json
{
  "id": 1,
  "emoji": "🚿",
  "phrase": "Take a shower!"
}
```

`id` is assigned by the server. `emoji` is trimmed and limited to 32 characters; `phrase` is
trimmed and limited to 280 characters. Blank values are rejected.

### Examples

```bash
# Create
curl -i -X POST http://localhost:8080/api/v1/phrases \
  -H 'Content-Type: application/json' \
  -d '{"emoji":"🚿","phrase":"Take a shower!"}'

# List
curl http://localhost:8080/api/v1/phrases

# Read one
curl http://localhost:8080/api/v1/phrases/1

# Delete
curl -i -X DELETE http://localhost:8080/api/v1/phrases/1

# HTML view
curl http://localhost:8080/phrases
```

Errors are JSON and use a stable `error` code plus a human-readable `message`:

```json
{
  "error": "validation_error",
  "message": "emoji must not be blank; phrase must not be blank"
}
```

| Status | `error` | Cause |
|---|---|---|
| `400` | `validation_error` | Blank/oversized fields, non-positive id |
| `400` | `invalid_body` | Body is not valid JSON for the endpoint |
| `400` | `bad_request` | Malformed request (for example, bad `Content-Type`) |
| `404` | `not_found` | No phrase with that id |
| `500` | `internal_error` | Unexpected failure (details are logged, not returned) |

## Run locally

Requires JDK 21.

```bash
./gradlew run
```

The server starts on <http://localhost:8080>. Override the port with `PORT`:

```bash
PORT=9090 ./gradlew run
```

### Fat JAR

```bash
./gradlew buildFatJar
java -jar build/libs/ktor-phrases-all.jar
```

`./gradlew runFatJar` builds and runs the same artifact in one step.

### Tests

```bash
./gradlew test
```

The suite covers creation, duplicate handling, listing, fetching, deletion, validation,
unknown ids, and malformed JSON. CI runs `./gradlew build` on every push and pull request.

## Configuration

| Setting | Default | Source |
|---|---|---|
| Port | `8080` | `application.conf`, overridable with `PORT` |
| Log level | `INFO` | `logback.xml` |

## Design notes and next steps

- The repository interface is deliberately small (`add`, `phrase`, `phrases`, `remove`, `clear`);
  swapping in a persistent implementation would not change the routes.
- Natural next steps: pagination, an OpenAPI description, SQLite/Exposed persistence, and
  Docker packaging (the Ktor plugin already exposes `buildImage` when Docker is available).
- There is no authentication or rate limiting. Run it as a demo, not as a production service.

## Credits

- Based on the 2019 Ray Wenderlich tutorial by [Ray Wenderlich](https://www.raywenderlich.com/2885892-server-side-kotlin-with-ktor).
- Maintained by [@Giancarmine](https://github.com/Giancarmine).

## License

[MIT](LICENSE)
