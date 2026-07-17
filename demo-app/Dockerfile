# Multi-stage build for the demo app. Built from the repo root; COPY . . brings the whole Maven
# reactor (parent + all module poms/src) so `-pl demo-app -am` resolves without per-module COPYs.
# (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl demo-app -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/demo-app/target/demo-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
