############ BUILD STAGE ############
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom first for dependency caching
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy full source (backend + frontend/) and build.
# The Maven build also builds frontend/ (npm ci && npm run build) straight into
# src/main/resources/static via frontend-maven-plugin, which auto-downloads Node itself -
# no Node needs to be preinstalled in this image.
COPY . .
RUN mvn -B clean package -DskipTests


############ RUNTIME STAGE ###########
FROM eclipse-temurin:21-jre

# Deliberately NOT /app: that path is reserved on the host for bind-mounting target apps'
# log/backup roots straight into the container at an identical path (see docker-compose.yml).
WORKDIR /opt/logutility

# curl isn't in the base JRE image; needed for the HEALTHCHECK below.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/*.jar app.jar

# uid 1000 collides with the base image's predefined "ubuntu" user - use a high uid instead.
RUN useradd --uid 10001 --create-home appuser \
    && chown -R appuser:appuser /opt/logutility
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

# END
