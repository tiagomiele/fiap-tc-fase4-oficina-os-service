# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN dos2unix mvnw || sed -i 's/\r$//' mvnw
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline || true

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM alpine:3.22 AS newrelic
ARG NEW_RELIC_AGENT_VERSION=9.3.0
ARG NEW_RELIC_AGENT_SHA256=8378bbe5db7e0736756ebb809dce653bd7ffd98d883afdb05bd8cfc2f0ea02fd
RUN apk add --no-cache curl unzip \
    && curl -fsSLo /tmp/newrelic-java.zip \
      "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEW_RELIC_AGENT_VERSION}/newrelic-java.zip" \
    && echo "${NEW_RELIC_AGENT_SHA256}  /tmp/newrelic-java.zip" | sha256sum -c - \
    && unzip -q /tmp/newrelic-java.zip -d /opt \
    && rm /tmp/newrelic-java.zip

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=builder /workspace/target/oficina-backend.jar /app/app.jar
COPY --from=newrelic /opt/newrelic/newrelic.jar /opt/newrelic/newrelic.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="" \
    NEW_RELIC_LOG_FILE_NAME=STDOUT \
    NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false \
    NEW_RELIC_APPLICATION_LOGGING_LOCAL_DECORATING_ENABLED=true
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
