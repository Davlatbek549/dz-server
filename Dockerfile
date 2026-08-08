# syntax=docker/dockerfile:1

# ---- Build ------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first: dependency resolution then caches as its own
# layer and only re-runs when the build files change, not on every source edit.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
# bootJar does not run tests, which matters here: the tests need a live
# Postgres, and the build image has none.
RUN ./gradlew --no-daemon clean bootJar

# ---- Run --------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Unprivileged user: nothing here needs root, and a compromised process should
# not get it for free.
RUN addgroup -S dz && adduser -S dz -G dz
COPY --from=build /workspace/build/libs/*.jar app.jar
USER dz

# Free-tier containers are typically 512 MB. Without these the JVM sizes its
# heap against the *host's* memory, over-commits, and gets OOM-killed under the
# first real load. SerialGC costs throughput but saves the memory that G1's
# bookkeeping would take, which is the right trade this small.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080

# exec so the JVM is PID 1 and receives the platform's shutdown signal directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
