# =============================================================================
#  PREPRO AI – Production Dockerfile (Multi-stage build)
# =============================================================================

# Stage 1: Build the Maven application
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Pre-fetch dependencies to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package executable fat jar
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal, secure JRE 17 runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/timetable.jar app.jar

# Expose default port
EXPOSE 8082
ENV PORT=8082
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

# Start the Spring Boot Web application
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
