#!/usr/bin/env bash
set -euo pipefail

echo "=== Testing Code Quality & Tooling Setup ==="

# Check config files
test -f .scalafmt.conf || { echo "Missing .scalafmt.conf"; exit 1; }
test -f .scalafix.conf || { echo "Missing .scalafix.conf"; exit 1; }

# Check that .scalafmt.conf has runner.dialect = scala3
grep -q "runner.dialect = scala3" .scalafmt.conf || { echo ".scalafmt.conf missing scala3 dialect"; exit 1; }

# Check that build.sbt contains strict compiler flags
grep -q "scalacOptions" build.sbt || { echo "build.sbt missing scalacOptions"; exit 1; }
grep -q -- "-Werror" build.sbt || { echo "build.sbt missing -Werror"; exit 1; }

# Run scalafmt check
sbt -Dsbt.supershell=false --batch scalafmtCheckAll

echo "=== Code Quality & Tooling setup verified! ==="
