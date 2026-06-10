#!/bin/bash

# Fineract Gradle Code Quality Checks
# Run this script to verify code quality before committing or building
# This script automatically fixes spotless formatting issues before running checks

set -e

echo "════════════════════════════════════════════════════════════════"
echo "  Running Fineract Code Quality Checks"
echo "════════════════════════════════════════════════════════════════"
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Stop Gradle daemon first
echo -e "${YELLOW}→ Stopping Gradle daemon...${NC}"
./gradlew --stop > /dev/null 2>&1 || true

# Auto-fix spotless formatting issues first
echo ""
echo -e "${BLUE}→ Auto-fixing code formatting with spotlessApply...${NC}"
./gradlew --no-daemon --console=plain spotlessApply

if [ $? -ne 0 ]; then
    echo ""
    echo -e "${RED}✗ Failed to apply spotless formatting.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Code formatting applied${NC}"
echo ""

# Run spotlessCheck and checkstyleMain
echo -e "${YELLOW}→ Running spotlessCheck and checkstyleMain...${NC}"
./gradlew --no-daemon --console=plain spotlessCheck checkstyleMain

if [ $? -ne 0 ]; then
    echo ""
    echo -e "${RED}✗ Code quality checks failed (spotless/checkstyle).${NC}"
    echo ""
    echo "Note: spotlessApply was already run. If spotlessCheck still fails,"
    echo "there may be issues that require manual intervention."
    echo ""
    echo "To fix checkstyle issues, review the output above and correct the violations."
    exit 1
fi

echo -e "${GREEN}✓ spotlessCheck and checkstyleMain passed${NC}"
echo ""

# Run spotlessCheck and spotBugsMain
echo -e "${YELLOW}→ Running spotlessCheck and spotBugsMain...${NC}"
./gradlew --no-daemon --console=plain spotlessCheck spotBugsMain

if [ $? -ne 0 ]; then
    echo ""
    echo -e "${RED}✗ Code quality checks failed (spotless/spotbugs).${NC}"
    echo ""
    echo "Note: spotlessApply was already run. If spotlessCheck still fails,"
    echo "there may be issues that require manual intervention."
    echo ""
    echo "To fix spotbugs issues, review the output above and correct the violations."
    exit 1
fi

echo -e "${GREEN}✓ spotlessCheck and spotBugsMain passed${NC}"
echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ All code quality checks passed!${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo ""

