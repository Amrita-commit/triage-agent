# Multi-stage build for terraform-mcp. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
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
