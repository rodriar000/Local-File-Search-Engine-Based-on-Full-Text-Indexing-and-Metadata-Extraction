#!/bin/bash

# Build script for tfg-filesearch Java backend
# This script locates Maven and builds the project

echo "🔨 Building tfg-filesearch Java Backend..."
echo ""

# Try to find Maven
MVN_CMD=""

# Check common locations
if command -v mvn &> /dev/null; then
    MVN_CMD="mvn"
elif [ -f "$HOME/.sdkman/candidates/maven/current/bin/mvn" ]; then
    MVN_CMD="$HOME/.sdkman/candidates/maven/current/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MVN_CMD="/usr/local/bin/mvn"
elif [ -f "./mvnw" ]; then
    MVN_CMD="./mvnw"
fi

if [ -z "$MVN_CMD" ]; then
    echo "❌ Maven not found!"
    echo ""
    echo "Please install Maven:"
    echo "  brew install maven"
    echo ""
    echo "Or use SDKMAN:"
    echo "  sdk install maven"
    exit 1
fi

echo "✓ Maven found: $MVN_CMD"
echo ""

# Build the project
echo "Building project..."
$MVN_CMD clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ BUILD SUCCESSFUL!"
    echo ""
    echo "📦 Generated JAR:"
    ls -lh target/*.jar
    echo ""
    echo "🚀 Run the CLI:"
    echo "  java -jar target/filesearch-1.0.0-jar-with-dependencies.jar --help"
else
    echo ""
    echo "❌ BUILD FAILED"
    echo ""
    echo "Check the errors above and try again."
    exit 1
fi
