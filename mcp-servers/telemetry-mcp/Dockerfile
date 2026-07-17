# Multi-stage build for telemetry-mcp. Built from the repo root so the Maven reactor
# (parent + copilot-core + telemetry-mcp) is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY copilot-core/pom.xml copilot-core/pom.xml
COPY demo-app/pom.xml demo-app/pom.xml
COPY mcp-servers/telemetry-mcp/pom.xml mcp-servers/telemetry-mcp/pom.xml
COPY mcp-servers/terraform-mcp/pom.xml mcp-servers/terraform-mcp/pom.xml
COPY agents/diagnostics-agent/pom.xml agents/diagnostics-agent/pom.xml
COPY agents/orchestrator/pom.xml agents/orchestrator/pom.xml
COPY agents/remediation-agent/pom.xml agents/remediation-agent/pom.xml
COPY approval-ui/pom.xml approval-ui/pom.xml
RUN mvn -q -pl copilot-core,mcp-servers/telemetry-mcp -am -DskipTests dependency:go-offline || true
COPY copilot-core/src copilot-core/src
COPY mcp-servers/telemetry-mcp/src mcp-servers/telemetry-mcp/src
RUN mvn -q -pl mcp-servers/telemetry-mcp -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/mcp-servers/telemetry-mcp/target/telemetry-mcp.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
