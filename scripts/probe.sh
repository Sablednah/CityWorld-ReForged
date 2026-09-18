#!/usr/bin/env bash
# Run one chunk probe headlessly and stop the server when it is done.
#
#   ./scripts/probe.sh <probe spec> [extra -D options...]
#   ./scripts/probe.sh find:HouseLot -Dcityworld.probe.radius=1 -Dcityworld.probe.layers=60..82
#   ./scripts/probe.sh 12,-7 -Dcityworld.watch=200,66,-100
#
# The probe itself (Support/ChunkProbe) generates the chunk(s), logs per-layer tallies, PLANvWORLD
# and whatever else its options ask for, then logs "PROBE complete" — it must never stop the server
# (CurseForge, see CLAUDE.md), so this script does: it waits for that line and kills the run's
# process group, exactly as selftest.sh does. Output goes to build/probe/<spec>.log, and the
# PROBE lines are echoed at the end. The world is regenerated every run (existing chunks never
# regenerate, so a stale one would probe old code); the seed is whatever run/server.properties says.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPEC="${1:?probe spec, e.g. find:HouseLot or 12,-7}"
shift
if [ -z "${JAVA_HOME:-}" ]; then
    case "$(grep -E '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')" in
        1.*) export JAVA_HOME="$ROOT/tools/jdk21" ;;
        *)   export JAVA_HOME="$ROOT/tools/jdk25" ;;
    esac
fi
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$ROOT/build/probe"
LOG="$ROOT/build/probe/$(echo "$SPEC" | tr ':,/' '___').log"
rm -rf "$ROOT/run/world"
export JAVA_TOOL_OPTIONS="-Dcityworld.probe=$SPEC $*"
echo ">> probe $SPEC $* (log: $LOG)"
set +e
set -m
"$ROOT/gradlew" runServer --console=plain > "$LOG" 2>&1 &
PID=$!
set +m
OWN="$(ps -o pgid= -p $$ 2>/dev/null | tr -d ' ')"
end_run() {
    [ "$PID" = "$OWN" ] && { echo "!! refusing to kill own process group" >&2; return; }
    kill -TERM "-$PID" 2>/dev/null
    for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "-$PID" 2>/dev/null || return; sleep 1; done
    kill -KILL "-$PID" 2>/dev/null
}
waited=0
while :; do
    if grep -q "PROBE complete\|PROBE failed" "$LOG" 2>/dev/null; then end_run; wait "$PID" 2>/dev/null; break; fi
    if ! kill -0 "$PID" 2>/dev/null; then wait "$PID"; echo "!! gradle exited before the probe finished ($?)" >&2; break; fi
    if [ "$waited" -ge "${CITYWORLD_PROBE_TIMEOUT:-900}" ]; then echo "!! timed out" >&2; end_run; break; fi
    sleep 2; waited=$((waited + 2))
done
grep -a "PROBE\|WATCH\|Exception\|ERROR\]" "$LOG" | grep -v "refmap\|DEBUG" | cut -c1-400
