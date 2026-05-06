FROM docker.m.daocloud.io/library/amazoncorretto:17-alpine

WORKDIR /app

COPY target/danmakuVideoAPI-1.0.0.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
