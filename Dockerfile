# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build files first so dependency resolution is cached.
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon build buildFatJar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --uid 10001 app \
    && mkdir -p /app/data \
    && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/build/libs/ktor-phrases-all.jar app.jar

USER app
ENV PORT=8080 \
    STORAGE=memory \
    DATABASE_PATH=/app/data/phrases.db
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
