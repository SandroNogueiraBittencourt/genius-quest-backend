# Java 17, conforme pom.xml do projeto.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /app/target/*.jar /app/app.jar
USER spring:spring
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
