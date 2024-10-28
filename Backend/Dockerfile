# Build stage
FROM maven:3.9.4-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .
# Download dependencies
RUN mvn dependency:go-offline

# Copy the source code
COPY . .

# Run Maven build
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy the jar file with the correct name
COPY --from=build /app/target/Backend-0.0.1-SNAPSHOT.jar app.jar

# Environment variable for the port (important for Koyeb)
ENV PORT=8080
EXPOSE 8080

# Use the correct jar name in ENTRYPOINT
ENTRYPOINT ["java", "-jar", "app.jar"]