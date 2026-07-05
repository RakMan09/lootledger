# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Warm the wrapper/dependency cache (best-effort; ignored on first run).
RUN ./gradlew --version
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/lootledger-*.jar app.jar
# Default to the Postgres-only demo profile so the image runs on free-tier hosts out of the box.
ENV SPRING_PROFILES_ACTIVE=demo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
