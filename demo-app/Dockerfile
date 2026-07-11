# Multi-stage build for the demo app. Built from the repo root so the whole
# Maven reactor (parent + copilot-core + demo-app) is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY copilot-core/pom.xml copilot-core/pom.xml
COPY demo-app/pom.xml demo-app/pom.xml
COPY mcp-servers/telemetry-mcp/pom.xml mcp-servers/telemetry-mcp/pom.xml
COPY agents/diagnostics-agent/pom.xml agents/diagnostics-agent/pom.xml
# Warm the dependency cache before copying sources.
RUN mvn -q -pl copilot-core,demo-app -am -DskipTests dependency:go-offline || true
COPY copilot-core/src copilot-core/src
COPY demo-app/src demo-app/src
RUN mvn -q -pl demo-app -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/demo-app/target/demo-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
