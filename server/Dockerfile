# Dockerfile for Ktor Server - builds in Cloud Build
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -s /bin/sh -D appuser

# JAR should be built by Cloud Build in /workspace/server/build/libs/
COPY server/build/libs/*-all.jar app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]