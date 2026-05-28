FROM gradle:8.11-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
