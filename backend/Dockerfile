# backend/Dockerfile — multi-stage build
#
# MENTOR NOTE — fixing the v1.3 "No Dockerfile" / DevOps:1/10 finding.
#
# Stage 1 (build): uses the full Maven+JDK image to compile and package the
# application into a fat JAR. This stage is large (~500MB+) but never ships —
# only its OUTPUT (the JAR) is copied into stage 2.
#
# Stage 2 (runtime): uses a slim JRE-only image (no compiler, no Maven, no
# build tools) to actually run the application. This keeps the final image
# small and reduces the attack surface — there's no `javac`, no Maven, no
# source code in the image that ships to production.
#
# Runs as a non-root user (appuser) — running containers as root is a
# well-known container security anti-pattern this Dockerfile avoids from
# the start.

# ── Stage 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the POM first so Docker can cache the dependency download layer
# independently of source code changes — subsequent builds that only change
# .java files skip re-downloading every dependency from Maven Central.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# Healthcheck hits the Actuator endpoint added in v2.0 — Kubernetes/Docker
# Compose can use this to know when the app is actually ready to serve
# traffic, not just "the process started."
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
