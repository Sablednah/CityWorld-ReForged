#!/usr/bin/env bash
# Run one checkout's self-test and clean up after it:   scripts/selftest-one.sh <checkout dir> <jdk17|jdk21|jdk25> <port>
# Prints one PASS/FAIL line and the first failures. The dev server the Gradle daemon launched can outlive
# selftest.sh's own process-group kill; it is found by its working directory (<checkout>/run) and killed by PID —
# never by a name pattern. Run at most TWO of these at once (five ran the machine out of memory), and never
# compile in a checkout while its self-test is running.
dir="$(cd "$1" && pwd)"; jdk=$2; port=$3
cd "$dir" || exit 1
export JAVA_HOME="$dir/tools/$jdk" PATH="$dir/tools/$jdk/bin:$PATH"
for i in $(seq 1 120); do
  alive=0; for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$dir/run" ] && alive=1; done
  [ $alive = 0 ] && break; sleep 1
done
CITYWORLD_SELFTEST_PORT=$port CITYWORLD_SELFTEST_TIMEOUT=5400 scripts/selftest.sh > build/selftest-one.log 2>&1
rc=$?
echo "$(basename "$dir") rc=$rc $(grep -a '^>> PASS\|^!!' build/selftest-one.log | head -2 | tr '\n' ' ')"
grep -a "SELFTEST:   -" build/selftest/*.log 2>/dev/null | cut -c1-240 | head -3
for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$dir/run" ] && kill "$p"; done
# ...and wait for it to be GONE. A killed server saves its world on the way out; a run started in this checkout
# before that finished had its fresh `rm -rf run/world` undone by that save (a world folder with no level.dat ->
# "Unable to read or access the world gen settings file" -> "Failed to load datapacks", 26.1 and 26.3, 2026-11-14).
for i in $(seq 1 120); do
  alive=0; for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$dir/run" ] && alive=1; done
  [ $alive = 0 ] && break; sleep 1
done
exit $rc
