# ktor_phrases

[![CI](https://github.com/Giancarmine/ktor_phrases/actions/workflows/ci.yml/badge.svg)](https://github.com/Giancarmine/ktor_phrases/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A small emoji/phrase association API built with [Ktor](https://ktor.io/). You POST an emoji
with a phrase, and the service stores it in memory or SQLite, exposes it as a paginated JSON
API, and renders it on a server-side FreeMarker page.

Originally written in 2019 following the Ray Wenderlich
["Server-side Kotlin with Ktor"](https://www.raywenderlich.com/2885892-server-side-kotlin-with-ktor)
tutorial. This repository has since been modernized: Ktor 1.2 → 3.6, Kotlin 1.3 → 2.4,
Gradle 4.10 → 9.8, Moshi → kotlinx.serialization, plus tests, coverage, OpenAPI, persistence,
and a Docker image.

## Stack

| Component | Version / choice |
|---|---|
| Kotlin | 2.4.21 |
| Ktor | 3.6.0 |
| Gradle | 9.8.2 |
| JDK | 21 (toolchain) |
| Serialization | kotlinx.serialization |
| Templating | FreeMarker |
| Persistence | In-memory or SQLite via `org.xerial:sqlite-jdbc` |
| API docs | OpenAPI 3.0.3 + Swagger UI (`ktor-server-swagger`) |
| Logging | Logback 1.5.38 |
| Tests | JUnit 5 + Ktor `testApplication`, JaCoCo coverage gate |

## Features

- REST API for creating, listing, fetching, and deleting emoji/phrase pairs.
- Pagination with `limit`/`offset`, total counts, and RFC 5988 `Link` headers.
- JSON error envelope with stable error codes.
- Optional durable SQLite storage; in-memory remains the default.
- Swagger UI at `/docs` and the raw spec at `/docs/openapi.yaml`.
- Server-rendered HTML view with HTML-escaped values.
- Environment-driven configuration with startup validation.
- Multi-stage Dockerfile that runs the tests during the image build.

## Status and breaking changes

- **Breaking:** the collection endpoint moved from `POST /api/v1/phrase` to
  `POST /api/v1/phrases`. The old singular path is no longer served.
- The Heroku `Procfile`, the dead `ktor-phrases.herokuapp.com` link, and the Shadow plugin
  configuration were removed. Deployment uses the fat JAR produced by the Ktor Gradle plugin.
- History used to be memory-only. It now defaults to memory but can be switched to SQLite
  with `STORAGE=sqlite`; the in-memory store is still cleared on restart.
- Pagination keeps the response shape: the list body is still a JSON array, with page metadata
  in response headers. The default page size is 20, so clients that need everything must pass
  a larger `limit` or follow the `Link` headers.
- There is no authentication or rate limiting. Run it as a demo, not as a production service.

## Architecture

```
HTTP client
    │
    ▼
Netty engine (io.ktor.server.netty.EngineMain)
    │  reads src/main/resources/application.conf (+ environment overrides)
    ▼
Application.module()
    ├── AppConfig.from(...) ............ validates all settings
    ├── createRepository(config)
    │     ├── InMemoryRepository ....... Mutex + LinkedHashMap
    │     └── SqliteRepository ......... guarded JDBC connection
    ▼
Application.configure(repository, config)
    ├── DefaultHeaders
    ├── StatusPages ................... maps exceptions to JSON errors
    ├── ContentNegotiation ............ kotlinx.serialization JSON
    ├── FreeMarker .................... templates/*.ftl
    └── routing
         ├── GET  /                     plain-text endpoint index
         ├── GET  /about                project info
         ├── GET  /phrases              HTML list (FreeMarker)
         ├── GET  /docs                 Swagger UI
         ├── GET  /docs/openapi.yaml    OpenAPI document
         └── /api/v1/phrases            JSON API
                  │
                  ▼
             Repository
```

`Application.module()` is the production entry point. It builds the configured repository and
delegates to `Application.configure(repository, config)`, which tests call directly to inject
an in-memory repository or custom settings. Repository calls are guarded so both backends are
safe under concurrent requests.

## Project layout

```
src/main/kotlin/com/ges/
├── Application.kt                 entry point, plugins, module wiring
├── api/Phrase.kt                  /api/v1/phrases routes, pagination, validation
├── config/AppConfig.kt            typed settings and startup validation
├── model/
│   ├── EmojiPhrase.kt             stored entity (id, emoji, phrase)
│   ├── PhraseRequest.kt           POST payload
│   └── Errors.kt                  ErrorResponse + domain exceptions
├── repository/
│   ├── Repository.kt              storage contract
│   ├── InMemoryRepository.kt      thread-safe in-memory implementation
│   └── SqliteRepository.kt        SQLite implementation (plain JDBC)
└── webapp/
    ├── Home.kt                    GET /
    ├── About.kt                   GET /about
    └── Phrases.kt                 GET /phrases (FreeMarker)
src/main/resources/
├── application.conf               defaults + environment overrides
├── openapi.yaml                   OpenAPI 3.0.3 document
├── logback.xml
└── templates/phrases.ftl          HTML view
src/test/kotlin/com/ges/
├── ApiTest.kt                     JSON API and pagination tests
├── WebappTest.kt                  HTML, home, and Swagger tests
├── RepositoryFactoryTest.kt       storage selection tests
├── TestSupport.kt                 shared testApplication helpers
├── config/AppConfigTest.kt        configuration parsing/validation tests
└── repository/                    in-memory and SQLite repository tests
Dockerfile                         multi-stage build and runtime image
```

## API

| Method | Path | Success | Notes |
|---|---|---|---|
| `GET` | `/` | `200` | Plain-text list of endpoints |
| `GET` | `/about` | `200` | Project and source link |
| `GET` | `/phrases` | `200` | HTML page rendered with FreeMarker |
| `GET` | `/docs` | `200` | Swagger UI |
| `GET` | `/docs/openapi.yaml` | `200` | OpenAPI 3.0.3 specification |
| `GET` | `/api/v1/phrases` | `200` | Paginated JSON array; supports `limit` and `offset` |
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
trimmed and limited to 280 characters. Both limits are configurable. Blank values are rejected.

### Pagination

`GET /api/v1/phrases` accepts:

| Parameter | Default | Rules |
|---|---|---|
| `limit` | `PAGE_SIZE` (`20`) | Integer from `1` to `MAX_PAGE_SIZE` (`100`) |
| `offset` | `0` | Integer greater than or equal to `0` |

The body remains a JSON array. Page metadata is returned in headers:

```http
HTTP/1.1 200 OK
X-Total-Count: 12
X-Offset: 4
X-Limit: 2
Link: </api/v1/phrases?limit=2&offset=6>; rel="next", </api/v1/phrases?limit=2&offset=2>; rel="prev"

[
  {
    "id": 5,
    "emoji": "☕",
    "phrase": "Coffee break"
  }
]
```

`Link` is only present when a next and/or previous page exists. Invalid pagination parameters
return `400` with `validation_error`.

### Examples

```bash
# Create
curl -i -X POST http://localhost:8080/api/v1/phrases \
  -H 'Content-Type: application/json' \
  -d '{"emoji":"🚿","phrase":"Take a shower!"}'

# First page (default size)
curl http://localhost:8080/api/v1/phrases

# Explicit page
curl -i 'http://localhost:8080/api/v1/phrases?limit=2&offset=4'

# Read one
curl http://localhost:8080/api/v1/phrases/1

# Delete
curl -i -X DELETE http://localhost:8080/api/v1/phrases/1

# HTML view
curl http://localhost:8080/phrases

# OpenAPI / Swagger UI
curl http://localhost:8080/docs/openapi.yaml
open http://localhost:8080/docs
```

Swagger UI loads its static assets from `https://unpkg.com/swagger-ui-dist`, so viewing
`/docs` requires network access to that CDN.

Errors are JSON and use a stable `error` code plus a human-readable `message`:

```json
{
  "error": "validation_error",
  "message": "emoji must not be blank; phrase must not be blank"
}
```

| Status | `error` | Cause |
|---|---|---|
| `400` | `validation_error` | Blank/oversized fields, invalid id, invalid `limit`/`offset` |
| `400` | `invalid_body` | Body is not valid JSON for the endpoint |
| `400` | `bad_request` | Malformed request (for example, bad `Content-Type`) |
| `404` | `not_found` | No phrase with that id |
| `500` | `internal_error` | Unexpected failure (details are logged, not returned) |

## Configuration

Defaults live in `src/main/resources/application.conf`. Every setting can be overridden with
the environment variable shown below. Values are validated at startup; invalid combinations
(for example, `MAX_PAGE_SIZE < PAGE_SIZE`) stop the application with a clear error.

| Variable | Accepted values | Default | Purpose |
|---|---|---|---|
| `PORT` | integer `1`–`65535` | `8080` | HTTP listen port |
| `STORAGE` | `memory`, `sqlite` (case-insensitive) | `memory` | Repository backend |
| `DATABASE_PATH` | file path, `jdbc:sqlite:...`, or `:memory:` | `data/phrases.db` | SQLite database; only used when `STORAGE=sqlite`. Missing parent directories are created |
| `PAGE_SIZE` | integer `>= 1` | `20` | Default number of items returned by the list endpoint |
| `MAX_PAGE_SIZE` | integer `>= PAGE_SIZE` | `100` | Largest accepted `limit` value |
| `MAX_EMOJI_LENGTH` | integer `>= 1` | `32` | Maximum emoji length in characters |
| `MAX_PHRASE_LENGTH` | integer `>= 1` | `280` | Maximum phrase length in characters |
| `LOG_LEVEL` | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` | `INFO` | Root Logback level |

Examples:

```bash
# Defaults: memory storage on port 8080
./gradlew run

# Durable SQLite database
STORAGE=sqlite DATABASE_PATH=data/phrases.db ./gradlew run

# Custom port and smaller pages
PORT=9090 PAGE_SIZE=10 MAX_PAGE_SIZE=50 ./gradlew run

# Verbose logging
LOG_LEVEL=DEBUG ./gradlew run
```

The same variables work with the fat JAR and the Docker image.

### Persistence

- `STORAGE=memory` (default) keeps data in a `LinkedHashMap`; restarting the process loses it.
- `STORAGE=sqlite` stores data in the file at `DATABASE_PATH`. The schema is created on startup,
  and an identical emoji/phrase pair is stored only once.
- `DATABASE_PATH=:memory:` gives a private, ephemeral SQLite database (useful for tests).
- The SQLite implementation uses one mutex-guarded JDBC connection. That is deliberate for a
  demo; use a connection pool and migrations if you need production throughput.

## Run locally

Requires JDK 21.

```bash
./gradlew run
```

The server starts on <http://localhost:8080>; see [Configuration](#configuration) for overrides.

### Fat JAR

```bash
./gradlew buildFatJar
java -jar build/libs/ktor-phrases-all.jar
```

`./gradlew runFatJar` builds and runs the same artifact in one step.

### Docker

The included multi-stage `Dockerfile` builds the fat JAR (running the tests first) and starts
it as a non-root user.

```bash
docker build -t ktor-phrases .

# In-memory storage
docker run --rm -p 8080:8080 ktor-phrases

# SQLite with a named volume so data survives container replacement
docker run --rm -p 8080:8080 \
  -e STORAGE=sqlite \
  -e DATABASE_PATH=/app/data/phrases.db \
  -v ktor-phrases-data:/app/data \
  ktor-phrases
```

The runtime image listens on `8080` and defaults to `STORAGE=memory`. The SQLite file and its
parent directory are writable by the unprivileged `app` user (uid `10001`).

### Tests

```bash
./gradlew test
```

The suite has 51 tests covering creation, duplicate handling, pagination, fetching, deletion,
validation, malformed JSON, HTML escaping, Swagger/OpenAPI, configuration parsing, repository
selection, and both repository implementations (including SQLite persistence across reconnects).
JaCoCo reports are written to `build/reports/jacoco/test/html`; `./gradlew build` enforces an
80% line-coverage minimum (currently about 96% lines / 89% branches). CI runs `./gradlew build`
on every push and pull request.

## Design notes and next steps

- The repository interface is small (`add`, `phrase`, paginated `phrases`, `count`, `remove`,
  `clear`), so storage can be swapped without touching the routes.
- Pagination was kept header-based to preserve the existing array response shape. A future v2
  could return an envelope (`items`, `total`, `limit`, `offset`) if a breaking change is
  acceptable.
- Natural next steps: authentication/API keys, rate limiting, a health/readiness endpoint,
  a connection pool plus schema migrations for heavier SQLite use, generated (rather than
  hand-written) OpenAPI, and publishing the Docker image from CI.
- There is no authentication or rate limiting. Run it as a demo, not as a production service.

## Credits

- Based on the 2019 Ray Wenderlich tutorial by [Ray Wenderlich](https://www.raywenderlich.com/2885892-server-side-kotlin-with-ktor).
- Maintained by [@Giancarmine](https://github.com/Giancarmine).

## License

[MIT](LICENSE)
