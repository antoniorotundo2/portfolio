# ── Stage 1: build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

RUN apt-get update && apt-get install -y curl unzip && \
    curl -fL "https://github.com/sbt/sbt/releases/download/v1.12.11/sbt-1.12.11.zip" \
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
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /build/target/scala-*/portfolio-assembly-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]