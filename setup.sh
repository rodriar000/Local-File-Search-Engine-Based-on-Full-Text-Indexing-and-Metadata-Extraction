#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== TFG FileSearch Setup ===${NC}"

# 1. Check Dependencies
echo "Checking dependencies..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed.${NC}"
    exit 1
fi
echo "Java: $(java -version 2>&1 | head -n 1)"

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed.${NC}"
    exit 1
fi
echo "Maven: $(mvn -version | head -n 1)"

if ! command -v npm &> /dev/null; then
    echo -e "${RED}Error: Node.js/npm is not installed.${NC}"
    exit 1
fi
echo "npm: $(npm -version)"

# 2. Build Backend
echo ""
echo -e "${GREEN}Building Backend (Java)...${NC}"
cd tfg-filesearch
mvn clean package -DskipTests
cd ..

# 3. Setup Frontend
echo ""
echo -e "${GREEN}Setting up Frontend (React)...${NC}"
cd tfg-filesearch-gui
if [ ! -f .env ]; then
    echo "Creating .env from .env.example"
    cp .env.example .env
fi
npm install
# Optional: build frontend to verify it compiles
npm run build
cd ..

echo ""
echo -e "${GREEN}Setup Complete!${NC}"
echo "Run './start.sh' to launch the application."
