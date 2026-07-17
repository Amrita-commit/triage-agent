# Multi-stage build for terraform-mcp. Built from the repo root so the Maven reactor is available.
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
RUN mvn -q -pl copilot-core,mcp-servers/terraform-mcp -am -DskipTests dependency:go-offline || true
COPY copilot-core/src copilot-core/src
COPY mcp-servers/terraform-mcp/src mcp-servers/terraform-mcp/src
RUN mvn -q -pl mcp-servers/terraform-mcp -am -DskipTests package

FROM eclipse-temurin:21-jre
# Install the Terraform CLI so the MCP server can run read-only plan/show.
ARG TERRAFORM_VERSION=1.9.8
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && curl -fsSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" -o /tmp/tf.zip \
    && unzip /tmp/tf.zip -d /usr/local/bin \
    && rm /tmp/tf.zip \
    && apt-get purge -y unzip && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/mcp-servers/terraform-mcp/target/terraform-mcp.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
