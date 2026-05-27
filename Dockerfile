# ── Stage 1: build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && apt-get install -y curl unzip && \
    curl -fL "https://github.com/sbt/sbt/releases/download/v1.10.1/sbt-1.10.1.zip" \
         -o /tmp/sbt.zip && \
    unzip /tmp/sbt.zip -d /opt && \
    ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt && \
    rm /tmp/sbt.zip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Cache dependencies first (layer cache trick)
COPY project/build.properties project/
COPY project/plugins.sbt      project/
COPY build.sbt                .
RUN sbt update

# Copy source and compile fat JAR
COPY src ./src
RUN sbt assembly

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /build/target/scala-3.4.2/portfolio-assembly-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
