# Multi-stage build for telemetry-mcp. COPY . . brings the whole Maven reactor (parent + all
# module poms/src) so `-pl` resolves without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl mcp-servers/telemetry-mcp -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/mcp-servers/telemetry-mcp/target/telemetry-mcp.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
