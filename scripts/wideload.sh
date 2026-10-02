#!/usr/bin/env bash
# Generate a wide block of chunks ALL AT ONCE in a dev server, let it tick, save, and stop it.
#   scripts/wideload.sh <checkout dir> <jdk> <chunkX,chunkZ,size>      e.g.  . jdk21 0,0,50
# Needs the temporary listener scripts/debug/WideLoad.java copied to
# src/main/java/me/daddychurchill/CityWorld/debug/ first — and DELETED before any commit or build that ships
# (find src build/classes -path '*debug*' must read 0). FORCED tickets on every chunk before any generates is
# what a spawn area or a teleport does, and the only way to see a decoration-thread deadlock (CLAUDE.md,
# "A hang at Preparing spawn area"). Afterwards read the world back with scripts/region_tally.py.
set -uo pipefail
ROOT="$(cd "$1" && pwd)"; cd "$ROOT"; export JAVA_HOME="$ROOT/tools/$2" PATH="$ROOT/tools/$2/bin:$PATH"
rm -rf run/world run/logs/latest.log
export JAVA_TOOL_OPTIONS="-Dcityworld.wideload=$3"
set -m; ./gradlew runServer --console=plain > "$ROOT/build/wideload.log" 2>&1 & PID=$!; set +m
for _ in $(seq 1 200); do grep -aq "WIDELOAD complete" run/logs/latest.log 2>/dev/null && break; kill -0 "$PID" 2>/dev/null || break; sleep 5; done
grep -a "WIDELOAD\|populateLots FAILED" run/logs/latest.log 2>/dev/null | cut -c60-2000 | head -40
grep -a "error:" "$ROOT/build/wideload.log" | head -3
kill -TERM "-$PID" 2>/dev/null; sleep 3; kill -KILL "-$PID" 2>/dev/null; wait "$PID" 2>/dev/null
for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$ROOT/run" ] && kill -9 "$p" 2>/dev/null; done; true
