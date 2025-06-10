# Stage 1: Build with Maven
FROM maven:3.8.6-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached unless pom.xml changes)
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package

# Stage 2: Runtime with JRE only
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]