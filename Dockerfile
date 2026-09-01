FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY flyway/build.gradle ./flyway/build.gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/wallet-ledger-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
