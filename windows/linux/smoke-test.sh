#!/usr/bin/env bash
# Starts the app under a virtual display, checks it is still running after a while, and saves a screenshot.
#
#   smoke-test.sh <screenshot.png> <program> [args...]
#
# Needs xvfb-run and ImageMagick's `import`. Fails if the app quits (or crashes) before the time is up.
set -euo pipefail
shot="$(readlink -f "$1")"; shift
seconds="${SMOKE_SECONDS:-20}"
log="${shot%.png}.log"
export SMOKE_SECONDS="$seconds" SMOKE_SHOT="$shot" SMOKE_LOG="$log"
xvfb-run -a -s "-screen 0 1280x800x24" bash -c '
  "$@" >"$SMOKE_LOG" 2>&1 &
  pid=$!
  sleep "$SMOKE_SECONDS"
  if ! kill -0 "$pid" 2>/dev/null; then
    wait "$pid"; code=$?
    echo "The app quit within $SMOKE_SECONDS s (exit code $code). Its output:"; cat "$SMOKE_LOG"
    exit 1
  fi
  import -window root "$SMOKE_SHOT"
  kill "$pid"; wait "$pid" 2>/dev/null || true
  echo "Still running after $SMOKE_SECONDS s; screenshot: $SMOKE_SHOT"
' smoke "$@"
