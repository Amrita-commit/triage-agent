# Multi-stage build for diagnostics-agent. Built from the repo root so the Maven reactor
# (parent + copilot-core + diagnostics-agent) is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY copilot-core/pom.xml copilot-core/pom.xml
COPY demo-app/pom.xml demo-app/pom.xml
COPY mcp-servers/telemetry-mcp/pom.xml mcp-servers/telemetry-mcp/pom.xml
COPY agents/diagnostics-agent/pom.xml agents/diagnostics-agent/pom.xml
RUN mvn -q -pl copilot-core,agents/diagnostics-agent -am -DskipTests dependency:go-offline || true
COPY copilot-core/src copilot-core/src
COPY agents/diagnostics-agent/src agents/diagnostics-agent/src
RUN mvn -q -pl agents/diagnostics-agent -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/agents/diagnostics-agent/target/diagnostics-agent.jar app.jar
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]
