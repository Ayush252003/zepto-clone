FROM eclipse-temurin:21-jdk-alpine-3.24 AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gradle.lockfile ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine-3.24
WORKDIR /app
RUN addgroup -S zepto && adduser -S zepto -G zepto
COPY --from=build /workspace/build/libs/zepto-clone-0.0.1-SNAPSHOT.jar /app/app.jar
USER zepto
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
