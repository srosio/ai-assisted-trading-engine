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

# Default JVM optimizations (can be overridden by JAVA_OPTS env var)
ENV JAVA_OPTS_DEFAULT="\
    -Xms64m \
    -Xmx180m \
    -XX:MaxMetaspaceSize=64m \
    -XX:ReservedCodeCacheSize=16m \
    -XX:+UseSerialGC \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start application - convert DATABASE_URL (postgres://) to JDBC format if provided
CMD ["sh", "-c", "\
    if [ -n \"$DATABASE_URL\" ]; then \
        export DB_URL=$(echo $DATABASE_URL | sed 's|postgres://|jdbc:postgresql://|' | sed 's|@|/|' | sed 's|:|/|3'); \
        export DB_USERNAME=$(echo $DATABASE_URL | sed 's|postgres://||' | cut -d: -f1); \
        export DB_PASSWORD=$(echo $DATABASE_URL | sed 's|postgres://||' | cut -d: -f2 | cut -d@ -f1); \
        export DB_URL=\"jdbc:postgresql://$(echo $DATABASE_URL | sed 's|postgres://[^@]*@||')\"; \
    fi; \
    java ${JAVA_OPTS:-$JAVA_OPTS_DEFAULT} -jar app.jar"]