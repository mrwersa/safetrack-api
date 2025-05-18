FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app
COPY . .
RUN mvn clean package -Dmaven.test.skip=true && \
    find /app/target -name '*.jar' -not -name '*sources.jar' -not -name '*javadoc.jar' -exec cp {} /app/target/app.jar \;

FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

