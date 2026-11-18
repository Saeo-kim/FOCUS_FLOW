# Production image for FOCUS FLOW. Needs a MySQL 8 database reachable through DB_* variables.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 focusflow && mkdir -p /data/uploads && chown focusflow /data/uploads
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER focusflow
ENV PORT=8080 SERVER_ADDRESS=0.0.0.0 SESSION_COOKIE_SECURE=true UPLOAD_DIR=/data/uploads
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://127.0.0.1:${PORT}/healthz || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
