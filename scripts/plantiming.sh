#!/usr/bin/env bash
# Time the plan-side costs (cold platmap grid, subway cross, lotAt, a find) on a fresh dev world and print the
# PLANTIMING lines.   scripts/plantiming.sh <checkout dir> <jdk>      e.g.  scripts/plantiming.sh . jdk21
# Copies scripts/debug/PlanTiming.java into the checkout's debug package for the run and DELETES it after
# (a shipped jar must have no *debug* class: find src build/classes -path '*debug*' must read 0).
set -uo pipefail
ROOT="$(cd "$1" && pwd)"; cd "$ROOT"; export JAVA_HOME="$ROOT/tools/$2" PATH="$ROOT/tools/$2/bin:$PATH"
DBG="$ROOT/src/main/java/me/daddychurchill/CityWorld/debug"
mkdir -p "$DBG" && cp "$(dirname "$0")/debug/PlanTiming.java" "$DBG/PlanTiming.java"
cleanup() { rm -rf "$DBG" "$ROOT/build/classes/java/main/me/daddychurchill/CityWorld/debug"; }
trap cleanup EXIT
rm -rf run/world run/logs/latest.log
export JAVA_TOOL_OPTIONS="-Dcityworld.plantiming=true"
set -m; ./gradlew runServer --console=plain > "$ROOT/build/plantiming.log" 2>&1 & PID=$!; set +m
for _ in $(seq 1 240); do grep -aq "PLANTIMING complete\|PLANTIMING failed" run/logs/latest.log 2>/dev/null && break; kill -0 "$PID" 2>/dev/null || break; sleep 5; done
grep -a "PLANTIMING" run/logs/latest.log 2>/dev/null | sed 's/.*PLANTIMING //' | head -40
grep -a "error:" "$ROOT/build/plantiming.log" | head -3
kill -TERM "-$PID" 2>/dev/null; sleep 3; kill -KILL "-$PID" 2>/dev/null; wait "$PID" 2>/dev/null
for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$ROOT/run" ] && kill -9 "$p" 2>/dev/null; done; true
