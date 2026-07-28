FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
