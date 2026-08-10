# Etapa 1: compilar el proyecto con Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Etapa 2: imagen final, liviana, solo con el jar ya compilado
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/compartix-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
