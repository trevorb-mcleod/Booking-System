# ── Build ─────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ── Run (no application.properties — prod config from env vars only) ───────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --home-dir /app appuser \
    && mkdir -p /data \
    && chown appuser:appuser /data

COPY --from=build /app/target/Booking-System-*.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser
ENV SPRING_PROFILES_ACTIVE=prod
ENV BOOKING_DB_PATH=/data/booking.db

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
