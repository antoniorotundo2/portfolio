# ── Stage 1: build JAR ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install SBT (alpine: use apk instead of apt)
RUN apk add --no-cache curl unzip bash && \
    curl -fL "https://github.com/sbt/sbt/releases/download/v1.10.1/sbt-1.10.1.zip" \
         -o /tmp/sbt.zip && \
    unzip /tmp/sbt.zip -d /opt && \
    ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt && \
    rm /tmp/sbt.zip

WORKDIR /build

# Cache dependencies layer
COPY project/build.properties project/
COPY project/plugins.sbt      project/
COPY build.sbt                .
RUN sbt update

# Compile fat JAR
COPY src ./src
RUN sbt assembly

# ── Stage 2: jlink — build minimal JRE ────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS jlink

COPY --from=builder /build/target/scala-3.4.2/portfolio-assembly-0.1.0.jar /app.jar

# Detect which JDK modules the fat JAR actually needs, then build a minimal JRE
RUN jdeps \
      --ignore-missing-deps \
      --print-module-deps \
      --multi-release 21 \
      --recursive \
      --class-path '' \
      /app.jar > /modules.txt && \
    # Always include java.logging for logback and jdk.crypto.ec for TLS
    echo ",java.logging,jdk.crypto.ec,jdk.crypto.cryptoki" >> /modules.txt && \
    MODULES=$(cat /modules.txt | tr -d '\n') && \
    echo "Modules: $MODULES" && \
    jlink \
      --add-modules "$MODULES" \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=2 \
      --output /jre-minimal

# ── Stage 3: runtime — scratch + minimal JRE ──────────────────────────────────
FROM alpine:3.20 AS runtime

# Minimal runtime deps
RUN apk add --no-cache tzdata && \
    addgroup -S app && adduser -S app -G app

COPY --from=jlink  /jre-minimal        /jre
COPY --from=builder /build/target/scala-3.4.2/portfolio-assembly-0.1.0.jar /app/app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["/jre/bin/java", \
  # Container-aware memory limits
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:InitialRAMPercentage=50.0", \
  # Use G1GC (better pause times) with small heap tuning for 512MB
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=100", \
  "-XX:G1HeapRegionSize=4m", \
  # Limit ZIO worker threads to available CPUs (1 on free tier)
  "-Dzio.worker-thread-count=2", \
  "-Dzio.blocking-thread-count=4", \
  # Faster startup: skip bytecode verification on trusted JAR
  "-Xverify:none", \
  "-jar", "/app/app.jar"]
