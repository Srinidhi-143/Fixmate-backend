FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home spring \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/fixmate-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER spring
ENV SERVER_PORT=8082
EXPOSE 8082
HEALTHCHECK --interval=20s --timeout=3s --start-period=45s --retries=5 \
  CMD curl --fail --silent http://localhost:${SERVER_PORT}/api/v1/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]