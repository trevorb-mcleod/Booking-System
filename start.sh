#!/bin/bash
# ── BookSmart start script ────────────────────────────────────────────────────
# Kills any existing process on port 8080, then starts the app.
# Usage:  ./start.sh

set -e

export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:$PATH"
export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
MVN="/Users/trevorbell/.m2/wrapper/dists/apache-maven-3.9.11/a2d47e15/bin/mvn"
PORT=8080

echo "──────────────────────────────────────────"
echo "  BookSmart Booking System"
echo "──────────────────────────────────────────"

# Kill whatever is currently on the port (safe to run even if nothing is there)
EXISTING=$(lsof -ti tcp:$PORT 2>/dev/null || true)
if [ -n "$EXISTING" ]; then
  echo "  Stopping existing process on port $PORT (PID $EXISTING)..."
  echo "$EXISTING" | xargs kill -9 2>/dev/null || true
  sleep 1
fi

echo "  Starting server on http://localhost:$PORT"
echo "  Press Ctrl+C to stop."
echo "──────────────────────────────────────────"

cd "$(dirname "$0")"
"$MVN" spring-boot:run

