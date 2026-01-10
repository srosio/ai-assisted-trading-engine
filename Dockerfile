FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src ./src

RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/ai-assisted-trading-engine-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]