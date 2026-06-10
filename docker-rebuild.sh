#!/bin/bash

# Fineract Docker - Rebuild After Code Changes
# Use this script when you've made changes to custom modules

set -e

echo "════════════════════════════════════════════════════════════════"
echo "  Rebuilding Fineract with Latest Changes"
echo "════════════════════════════════════════════════════════════════"
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Ensure Docker is in PATH for Gradle
DOCKER_PATH=$(which docker)
if [ -z "$DOCKER_PATH" ]; then
    echo -e "${RED}✗ Docker not found in PATH. Please ensure Docker Desktop is installed.${NC}"
    exit 1
fi

# Export PATH to ensure Gradle can find Docker
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running. Please start Docker Desktop and try again.${NC}"
    exit 1
fi

# Stop Gradle daemon first
echo -e "${YELLOW}→ Stopping Gradle daemon...${NC}"
./gradlew --stop > /dev/null 2>&1 || true

# Note: Run ./gradle-checks.sh separately before committing
# This allows you to test features quickly without waiting for checks

# Rebuild the custom Docker image
echo -e "${YELLOW}→ Rebuilding Docker image with latest code...${NC}"

# Get Docker path and set PATH
DOCKER_BIN=$(which docker)
if [ -z "$DOCKER_BIN" ]; then
    DOCKER_BIN="/usr/local/bin/docker"
fi

export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"
export DOCKER_HOST=unix:///var/run/docker.sock 2>/dev/null || true

# Run without daemon to ensure fresh environment
PATH="/usr/local/bin:/usr/bin:/bin:$PATH" ./gradlew --no-daemon :custom:docker:jibDockerBuild -x test -x rat -x custom:rat -x checkstyleMain -x checkstyleTest

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo -e "${GREEN}✓ Build successful${NC}"
echo ""

# Recreate and restart the container
echo -e "${YELLOW}→ Recreating Fineract container...${NC}"
docker-compose -f docker-compose-local.yml up -d --force-recreate fineract

echo -e "${GREEN}✓ Container recreated${NC}"
echo ""

# Wait a bit for startup
echo -e "${YELLOW}→ Waiting for Fineract to start (30 seconds)...${NC}"
sleep 30

# Check health
HTTP_STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" "https://localhost:8443/fineract-provider/actuator/health" 2>/dev/null || echo "000")

if [ "$HTTP_STATUS" = "200" ]; then
    echo -e "${GREEN}✓ Fineract is running!${NC}"
    echo ""
    echo "You can now test your changes:"
    echo "  docker-compose -f docker-compose-local.yml logs -f fineract"
else
    echo -e "${YELLOW}⚠ Fineract is still starting up. Check logs with:${NC}"
    echo "  docker-compose -f docker-compose-local.yml logs -f fineract"
fi
echo ""

