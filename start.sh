#!/bin/bash
# ── BookSmart start script ────────────────────────────────────────────────────
# Kills any existing process on port 8080, then starts the app.
# Usage:  ./start.sh

set -e

PORT=8080
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ -x "$ROOT/mvnw" ]; then
  MVN="$ROOT/mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
else
  echo "Error: Maven not found. Install Maven or use the included ./mvnw wrapper."
  exit 1
fi

echo "──────────────────────────────────────────"
echo "  BookSmart Booking System"
echo "──────────────────────────────────────────"

# Free the port when lsof is available (macOS/Linux)
if command -v lsof >/dev/null 2>&1; then
  EXISTING=$(lsof -ti tcp:"$PORT" 2>/dev/null || true)
  if [ -n "$EXISTING" ]; then
    echo "  Stopping existing process on port $PORT (PID $EXISTING)..."
    echo "$EXISTING" | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
fi

if [ ! -f "$ROOT/src/main/resources/application.properties" ]; then
  echo ""
  echo "  Config missing: copy application.properties.example first:"
  echo "    cp src/main/resources/application.properties.example \\"
  echo "       src/main/resources/application.properties"
  echo ""
  exit 1
fi

echo "  Starting server on http://localhost:$PORT"
echo "  Press Ctrl+C to stop."
echo "──────────────────────────────────────────"

exec "$MVN" spring-boot:run
