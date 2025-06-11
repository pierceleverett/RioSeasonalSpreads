# Build stage
# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy only the POM first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
COPY data ./data
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Manually add POI dependencies (temporary fix)
COPY --from=builder /root/.m2/repository/org/apache/poi/poi-ooxml/5.2.3/poi-ooxml-5.2.3.jar .
COPY --from=builder /root/.m2/repository/org/apache/poi/poi/5.2.3/poi-5.2.3.jar .
COPY --from=builder /app/target/app.jar .

CMD ["java", "-cp", "app.jar:poi-5.2.3.jar:poi-ooxml-5.2.3.jar", "Server"]

# Copy only the JAR and data (no build artifacts)
COPY --from=builder /app/target/app.jar .
COPY --from=builder /app/data ./data

# Run as non-root user for security
RUN useradd -m appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]