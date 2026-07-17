# Multi-stage build for diagnostics-agent. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl agents/diagnostics-agent -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/agents/diagnostics-agent/target/diagnostics-agent.jar app.jar
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]
