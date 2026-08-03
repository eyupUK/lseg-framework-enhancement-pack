FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services ./services
COPY quality-engineering-tests/pom.xml ./quality-engineering-tests/pom.xml
RUN mvn -B -pl services/inventory-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
