# Multi-stage build for the eval harness. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl evals -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/evals/target/evals.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
