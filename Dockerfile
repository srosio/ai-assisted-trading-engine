# Build stage - use JDK for compilation
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Gradle wrapper and dependencies first (for layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer if build files don't change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build with optimizations for low CPU
RUN ./gradlew build -x test --no-daemon \
    --parallel \
    --max-workers=1 \
    -Dorg.gradle.jvmargs="-Xmx256m -XX:MaxMetaspaceSize=128m"

# Runtime stage - optimized for 512MB RAM
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Copy JAR from build stage
COPY --from=build /app/build/libs/ai-assisted-trading-engine-1.0.0.jar app.jar

# Create non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# JVM optimizations for 512MB RAM and low CPU
ENV JAVA_OPTS="\
    -Xms128m \
    -Xmx384m \
    -XX:MaxMetaspaceSize=128m \
    -XX:ReservedCodeCacheSize=32m \
    -XX:+UseSerialGC \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+UseStringDeduplication \
    -XX:MinHeapFreeRatio=20 \
    -XX:MaxHeapFreeRatio=40 \
    -XX:GCTimeRatio=4 \
    -XX:AdaptiveSizePolicyWeight=90 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start application with optimized JVM settings
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]