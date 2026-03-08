#!/bin/bash

# English Learning Platform - Unified Test Runner
# This script executes both backend and frontend unit tests.

# Set colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Starting All Unit Tests ===${NC}"

# 1. Backend Tests (Java)
echo -e "\n${GREEN}[1/2] Running Backend Tests (JUnit)...${NC}"
cd backend
# Using the specific JAVA_HOME required for this environment
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home mvn test
BACKEND_EXIT_CODE=$?

if [ $BACKEND_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ Backend tests passed!${NC}"
else
    echo -e "${RED}✗ Backend tests failed!${NC}"
fi

cd ..

# 2. Frontend Tests (Node.js)
echo -e "\n${GREEN}[2/2] Running Frontend Tests (Node.js)...${NC}"
cd frontend
node src/utils/test-sm2.js
FRONTEND_EXIT_CODE=$?

if [ $FRONTEND_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ Frontend tests passed!${NC}"
else
    echo -e "${RED}✗ Frontend tests failed!${NC}"
fi

cd ..

# Final Summary
echo -e "\n${GREEN}=== Test Summary ===${NC}"
if [ $BACKEND_EXIT_CODE -eq 0 ] && [ $FRONTEND_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}All tests passed successfully!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed. Please check the logs above.${NC}"
    exit 1
fi
