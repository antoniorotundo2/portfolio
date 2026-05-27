FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/scala-3.4.2/portfolio-assembly-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
