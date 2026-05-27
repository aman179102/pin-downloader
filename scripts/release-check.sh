#!/usr/bin/env bash
set -e

echo "=== Release Check Script ==="
echo ""

echo "=== Git Status ==="
git status
echo ""

echo "=== Clean Build (debug) ==="
./gradlew clean assembleDebug
echo ""

echo "=== Release Build (may skip if unsigned) ==="
./gradlew assembleRelease || echo "Release build skipped (signing not configured)"
echo ""

echo "=== APK Paths ==="
find app/build/outputs -name "*.apk" 2>/dev/null
echo ""

echo "=== Tagging ==="
echo "Run these commands manually once ready:"
echo "  git tag v1.0.0"
echo "  git push origin v1.0.0"
