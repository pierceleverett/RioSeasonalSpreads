# Build stage
FROM maven:3.8.6-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
# Cache dependencies
RUN mvn dependency:go-offline
COPY src ./src
# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar
# Expose the correct port (matches your Server.java)
EXPOSE 8080
# Run the application
CMD ["java", "-jar", "app.jar"]