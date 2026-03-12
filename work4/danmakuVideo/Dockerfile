FROM openjdk:17-jdk-slim

WORKDIR /app

COPY danmakuVideoAPI-1.0.0.jar app.jar

EXPOSE 10001

ENTRYPOINT ["java", "-jar", "app.jar"]