#FROM maven:3-amazoncorretto-21 AS build
#COPY . .
#RUN mvn clean package -DskipTests
#
#FROM openjdk:21-jdk-slim
#COPY --from=build /target/Backend-0.0.1-SNAPSHOT.jar backend.jar
#EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "backend.jar"]



# Build stage
FROM maven:3-amazoncorretto-21 AS build
# Copy the POM file first to leverage Docker cache for dependencies
COPY pom.xml .
# Copy the source code
COPY src ./src
# Run Maven build
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:21-jdk-slim
# Copy only the built JAR from the build stage
COPY --from=build /target/Backend-0.0.1-SNAPSHOT.jar backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]