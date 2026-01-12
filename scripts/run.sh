#!/bin/bash

# Simple script to run the AI Trading Engine JAR

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}AI Trading Engine - Starting...${NC}"
echo ""

# Find JAR file
JAR_FILE=""
if [ -f "build/libs/ai-assisted-trading-engine-1.0.0.jar" ]; then
    JAR_FILE="build/libs/ai-assisted-trading-engine-1.0.0.jar"
elif [ -f "ai-assisted-trading-engine.jar" ]; then
    JAR_FILE="ai-assisted-trading-engine.jar"
elif [ -f "/opt/ai-trading-engine/ai-assisted-trading-engine.jar" ]; then
    JAR_FILE="/opt/ai-trading-engine/ai-assisted-trading-engine.jar"
else
    echo -e "${RED}Error: JAR file not found${NC}"
    echo ""
    echo "Please build the project first:"
    echo "  ./gradlew build"
    echo ""
    echo "Or specify JAR location:"
    echo "  JAR_FILE=/path/to/jar ./scripts/run.sh"
    exit 1
fi

echo -e "${GREEN}Using JAR: ${JAR_FILE}${NC}"

# Load environment variables if available
ENV_FILE=""
if [ -f ".env" ]; then
    ENV_FILE=".env"
elif [ -f "scripts/environment" ]; then
    ENV_FILE="scripts/environment"
elif [ -f "/etc/ai-trading-engine/environment" ]; then
    ENV_FILE="/etc/ai-trading-engine/environment"
fi

if [ -n "$ENV_FILE" ]; then
    echo -e "${GREEN}Loading environment from: ${ENV_FILE}${NC}"
    set -a
    source "$ENV_FILE"
    set +a
else
    echo -e "${YELLOW}No environment file found (.env, scripts/environment, /etc/ai-trading-engine/environment)${NC}"
    echo -e "${YELLOW}Using system environment variables${NC}"
fi

echo ""

# JVM Options (can be overridden with JAVA_OPTS environment variable)
JVM_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"

# Spring Profile (default to production if not set)
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-production}"

echo -e "${GREEN}Configuration:${NC}"
echo "  JVM Options: $JVM_OPTS"
echo "  Spring Profile: $SPRING_PROFILE"
echo ""

# Run the application
echo -e "${GREEN}Starting application...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

exec java $JVM_OPTS \
    -Dspring.profiles.active=$SPRING_PROFILE \
    -jar "$JAR_FILE"
