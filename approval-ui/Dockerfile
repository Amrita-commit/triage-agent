# Multi-stage build for approval-ui. COPY . . brings the whole Maven reactor so `-pl` resolves
# without per-module COPYs. (.dockerignore excludes target/, .git, .env.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl approval-ui -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/approval-ui/target/approval-ui.jar app.jar
EXPOSE 8130
ENTRYPOINT ["java", "-jar", "app.jar"]
