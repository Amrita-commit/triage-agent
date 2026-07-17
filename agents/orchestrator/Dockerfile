# Multi-stage build for the orchestrator. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl agents/orchestrator -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/agents/orchestrator/target/orchestrator.jar app.jar
EXPOSE 8110
ENTRYPOINT ["java", "-jar", "app.jar"]
