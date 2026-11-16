# Build context is the repository root (see docker-compose.yml).
# certs/ holds optional extra CA certificates, needed only on networks
# with TLS inspection.

FROM gradle:9.9.1-jdk25 AS build
COPY certs/ /tmp/certs/
RUN for f in /tmp/certs/*.crt; do \
      [ -f "$f" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$f" .crt)" -file "$f"; \
    done
WORKDIR /app
COPY backend/settings.gradle backend/build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null
COPY backend/src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
COPY certs/ /tmp/certs/
RUN for f in /tmp/certs/*.crt; do \
      [ -f "$f" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$f" .crt)" -file "$f"; \
    done && rm -rf /tmp/certs
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
