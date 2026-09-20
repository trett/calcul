#!/usr/bin/env bash
set -euo pipefail

echo "=== Testing Multi-Module sbt Configuration ==="

# Check files exist
test -f project/build.properties || { echo "Missing project/build.properties"; exit 1; }
test -f project/plugins.sbt || { echo "Missing project/plugins.sbt"; exit 1; }
test -f build.sbt || { echo "Missing build.sbt"; exit 1; }

# Verify sbt projects output contains shared, backend, and frontend
OUTPUT=$(sbt -Dsbt.supershell=false --batch projects)
echo "$OUTPUT"

echo "$OUTPUT" | grep -q "backend" || { echo "Project 'backend' not found in sbt projects"; exit 1; }
echo "$OUTPUT" | grep -q "frontend" || { echo "Project 'frontend' not found in sbt projects"; exit 1; }
echo "$OUTPUT" | grep -q "shared" || { echo "Project 'shared' not found in sbt projects"; exit 1; }

echo "=== All build structure checks passed! ==="
