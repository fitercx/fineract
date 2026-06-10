#!/bin/bash

# Fineract Docker - One Click Setup & Run
# This script builds the custom Docker image (with all custom modules) and starts Fineract

set -e  # Exit on error

echo "════════════════════════════════════════════════════════════════"
echo "  Fineract Local Docker Setup - One Click Build & Run"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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

echo -e "${GREEN}✓ Docker found at: $DOCKER_PATH${NC}"

# Check if PostgreSQL is accessible
echo -e "${YELLOW}→ Checking PostgreSQL connection...${NC}"
if ! psql -U fineract -d fineract_tenants -c "SELECT 1" > /dev/null 2>&1; then
    echo -e "${RED}✗ Cannot connect to PostgreSQL. Please ensure PostgreSQL is running.${NC}"
    echo -e "${YELLOW}  Expected: PostgreSQL user 'fineract' with password 'mysql'${NC}"
    echo -e "${YELLOW}  Databases: fineract_tenants, fineract_default${NC}"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL connection OK${NC}"
echo ""

# Step 1: Stop Gradle daemon to clear cached environment
echo -e "${YELLOW}→ Stopping Gradle daemon to ensure fresh environment...${NC}"
./gradlew --stop > /dev/null 2>&1 || true
echo -e "${GREEN}✓ Gradle daemon stopped${NC}"
echo ""

# Step 2: Build the custom Docker image (includes all custom modules)
echo -e "${YELLOW}→ Building Fineract custom Docker image (this may take 2-3 minutes)...${NC}"
echo -e "${YELLOW}  This image includes ALL custom modules from custom/crediblex/${NC}"

# Get Docker path
DOCKER_BIN=$(which docker)
if [ -z "$DOCKER_BIN" ]; then
    DOCKER_BIN="/usr/local/bin/docker"
fi

# Ensure Gradle can find Docker by setting PATH explicitly
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

# Export DOCKER_HOST if needed (for Docker Desktop)
export DOCKER_HOST=unix:///var/run/docker.sock 2>/dev/null || true

# Run Gradle with explicit PATH and without daemon to ensure fresh environment
PATH="/usr/local/bin:/usr/bin:/bin:$PATH" ./gradlew --no-daemon :custom:docker:jibDockerBuild -x test -x rat -x custom:rat -x checkstyleMain -x checkstyleTest

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Docker image 'fineract-custom:latest' built successfully${NC}"
else
    echo -e "${RED}✗ Failed to build Docker image${NC}"
    exit 1
fi
echo ""

# Step 3: Stop any existing containers
echo -e "${YELLOW}→ Stopping existing Fineract containers...${NC}"
docker-compose -f docker-compose-local.yml down > /dev/null 2>&1 || true
echo -e "${GREEN}✓ Containers stopped${NC}"
echo ""

# Step 4: Start Fineract with docker-compose
echo -e "${YELLOW}→ Starting Fineract container...${NC}"
docker-compose -f docker-compose-local.yml up -d
echo -e "${GREEN}✓ Fineract container started${NC}"
echo ""

# Step 5: Wait for application to be ready
echo -e "${YELLOW}→ Waiting for Fineract to start (this takes 30-60 seconds)...${NC}"
MAX_ATTEMPTS=40
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    
    # Check health endpoint
    HTTP_STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" "https://localhost:8443/fineract-provider/actuator/health" 2>/dev/null || echo "000")
    
    if [ "$HTTP_STATUS" = "200" ]; then
        echo -e "${GREEN}✓ Fineract is ready!${NC}"
        break
    fi
    
    if [ $((ATTEMPT % 5)) -eq 0 ]; then
        echo -e "  Still waiting... (attempt $ATTEMPT/$MAX_ATTEMPTS)"
    fi
    
    sleep 3
done

if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
    echo -e "${RED}✗ Fineract did not start within the expected time${NC}"
    echo -e "${YELLOW}  Check logs with: docker-compose -f docker-compose-local.yml logs -f fineract${NC}"
    exit 1
fi
echo ""

# Step 6: Verify the application
echo "════════════════════════════════════════════════════════════════"
echo -e "${GREEN}  🎉 Fineract is running successfully!${NC}"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo -e "${GREEN}✓ Health Check:${NC}"
curl -k -s "https://localhost:8443/fineract-provider/actuator/health" | python3 -m json.tool 2>/dev/null || echo "OK"
echo ""
echo ""

echo "📝 Quick Test Commands:"
echo "────────────────────────────────────────────────────────────────"
echo ""
echo "  # Test creditlines API:"
echo "  curl -k -u mifos:password \\"
echo "    -H \"Fineract-Platform-TenantId: default\" \\"
echo "    \"https://localhost:8443/fineract-provider/api/v1/clients/1/creditlines\""
echo ""
echo "  # View logs:"
echo "  docker-compose -f docker-compose-local.yml logs -f fineract"
echo ""
echo "  # Stop Fineract:"
echo "  docker-compose -f docker-compose-local.yml down"
echo ""
echo "────────────────────────────────────────────────────────────────"
echo ""
echo -e "${GREEN}✓ Setup complete! You can now test your features.${NC}"
echo ""

