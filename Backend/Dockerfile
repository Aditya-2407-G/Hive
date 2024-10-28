FROM maven:3-amazoncorretto-21 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
COPY --from=build /target/Backend-0.0.1-SNAPSHOT.jar Backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "Backend.jar"]