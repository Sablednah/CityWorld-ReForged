#!/usr/bin/env bash
# Run the dev server under the chunk probe and stop it when the probe reports complete.
#   scripts/probe.sh "<java -D options>"   e.g. "-Dcityworld.probe=0,0 -Dcityworld.probe.radius=10"
# Deletes run/world first (a probe wants fresh chunks), passes the options through JAVA_TOOL_OPTIONS,
# waits for "PROBE complete", then kills the whole process group by job-control PGID -- the same
# rule as selftest.sh: never a pkill pattern, never the gradle pid alone. Log: build/probe.log.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# ⚠ Also the old log: the wait below greps latest.log for "PROBE complete", and a stale line from the
# previous run makes it kill this one before the server has started (three silent non-runs, 2026-09-25).
# A server left by an earlier run that timed out holds the port and session.lock: the next one then fails to
# bind and reads like a code fault (2026-11-03). The daemon forks the server outside our process group, so the
# group kill below cannot reach it; find it by its working directory, as selftest-one.sh does. PIDs only.
stop_leftover() {
    for p in $(pgrep java); do
        [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$ROOT/run" ] || continue
        kill "$p"
        for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$p" 2>/dev/null || break; sleep 1; done
        kill -KILL "$p" 2>/dev/null
    done
}
stop_leftover
rm -rf run/world run/logs/latest.log
export JAVA_TOOL_OPTIONS="${1:-}"
LOG="$ROOT/build/probe.log"
set -m
./gradlew runServer --console=plain > "$LOG" 2>&1 &
PID=$!
set +m
for _ in $(seq 1 150); do
    grep -q "PROBE complete" run/logs/latest.log 2>/dev/null && break
    kill -0 "$PID" 2>/dev/null || break
    sleep 6
done
grep -a "PROBE: dimension\|PROBE complete\|FAILED" run/logs/latest.log | head -3 | cut -c60-200
kill -TERM "-$PID" 2>/dev/null
for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "-$PID" 2>/dev/null || break; sleep 1; done
kill -KILL "-$PID" 2>/dev/null
wait "$PID" 2>/dev/null
stop_leftover   # a probe that never reported complete leaves its server running
echo "probe run finished; regions: $(ls run/world/region 2>/dev/null | wc -l)"
