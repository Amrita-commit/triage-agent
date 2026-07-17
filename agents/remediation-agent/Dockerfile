# Multi-stage build for remediation-agent. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl agents/remediation-agent -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/agents/remediation-agent/target/remediation-agent.jar app.jar
EXPOSE 8120
ENTRYPOINT ["java", "-jar", "app.jar"]
