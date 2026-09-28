FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY shared ./shared
COPY services ./services
ARG MODULE
RUN mvn -q -B -f pom.xml -pl "services/${MODULE}" -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE
COPY --from=build /app/services/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]