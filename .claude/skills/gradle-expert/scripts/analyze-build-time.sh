#!/bin/bash
# Analyze Gradle build performance and generate report

set -e

PROJECT_ROOT="${1:-.}"
cd "$PROJECT_ROOT"

echo "🔍 Analyzing Gradle build performance..."
echo "========================================"
echo ""

# Clean build for accurate timing
echo "Running clean build with --profile..."
./gradlew clean build --profile --scan

# Find the latest profile report
PROFILE_REPORT=$(find build/reports/profile -name "*.html" -type f -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")

if [ -n "$PROFILE_REPORT" ]; then
    echo ""
    echo "✅ Profile report generated: $PROFILE_REPORT"
    echo ""
    echo "📊 Build Performance Summary:"
    echo "----------------------------"

    # Extract key metrics if available
    if command -v jq &> /dev/null && [ -f "build/reports/profile/profile.json" ]; then
        jq -r '.buildTime, .taskExecutionTime' build/reports/profile/profile.json
    else
        echo "Open the HTML report for detailed analysis:"
        echo "file://$PWD/$PROFILE_REPORT"
    fi
else
    echo "⚠️  Profile report not found"
fi

echo ""
echo "💡 Build optimization tips:"
echo "- Enable Gradle daemon: org.gradle.daemon=true"
echo "- Parallel execution: org.gradle.parallel=true"
echo "- Configuration cache: org.gradle.configuration-cache=true"
echo "- Build cache: org.gradle.caching=true"
echo ""
echo "Add these to gradle.properties for faster builds"
