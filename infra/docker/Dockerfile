# One Dockerfile for every Java service: the module to build is passed in as MODULE.
#   docker build --build-arg MODULE=flight-service -t skyflow-flight-service .
#
# The build needs the whole reactor on the context because the services share the common and
# cluster-core modules, so `-am` builds those first.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Poms first: dependency resolution is then cached separately from source changes.
COPY pom.xml ./
COPY services/common/pom.xml services/common/
COPY services/cluster-core/pom.xml services/cluster-core/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/user-service/pom.xml services/user-service/
COPY services/flight-service/pom.xml services/flight-service/
COPY services/booking-service/pom.xml services/booking-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/ai-service/pom.xml services/ai-service/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY services ./services

ARG MODULE
RUN test -n "$MODULE" || (echo "MODULE build-arg is required" && exit 1) \
    && mvn -B -pl services/${MODULE} -am -DskipTests package \
    && cp services/${MODULE}/target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl is only here for the container health check below.
RUN apk add --no-cache curl \
    && addgroup -S skyflow && adduser -S skyflow -G skyflow

COPY --from=build /build/app.jar app.jar
RUN chown -R skyflow:skyflow /app
USER skyflow

# Heap is left to the JVM's container-aware defaults; the percentage matters more than a fixed -Xmx
# when the memory limit changes between environments.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:${PORT}/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
