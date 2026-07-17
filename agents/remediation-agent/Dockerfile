# Multi-stage build for remediation-agent. Built from the repo root so the Maven reactor is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy every module POM so the parent reactor (which lists all modules) resolves.
COPY pom.xml .
COPY copilot-core/pom.xml copilot-core/pom.xml
COPY demo-app/pom.xml demo-app/pom.xml
COPY mcp-servers/telemetry-mcp/pom.xml mcp-servers/telemetry-mcp/pom.xml
COPY mcp-servers/terraform-mcp/pom.xml mcp-servers/terraform-mcp/pom.xml
COPY agents/diagnostics-agent/pom.xml agents/diagnostics-agent/pom.xml
COPY agents/orchestrator/pom.xml agents/orchestrator/pom.xml
COPY agents/remediation-agent/pom.xml agents/remediation-agent/pom.xml
COPY approval-ui/pom.xml approval-ui/pom.xml
RUN mvn -q -pl copilot-core,agents/remediation-agent -am -DskipTests dependency:go-offline || true
COPY copilot-core/src copilot-core/src
COPY agents/remediation-agent/src agents/remediation-agent/src
RUN mvn -q -pl agents/remediation-agent -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/agents/remediation-agent/target/remediation-agent.jar app.jar
EXPOSE 8120
ENTRYPOINT ["java", "-jar", "app.jar"]
