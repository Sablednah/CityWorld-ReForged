#!/usr/bin/env bash
# Run one checkout's self-test and clean up after it:   scripts/selftest-one.sh <checkout dir> <jdk17|jdk21|jdk25> <port>
# Prints one PASS/FAIL line and the first failures. The dev server the Gradle daemon launched can outlive
# selftest.sh's own process-group kill; it is found by its working directory (<checkout>/run) and killed by PID —
# never by a name pattern. Run at most TWO of these at once (five ran the machine out of memory), and never
# compile in a checkout while its self-test is running.
dir="$(cd "$1" && pwd)"; jdk=$2; port=$3
cd "$dir" || exit 1
export JAVA_HOME="$dir/tools/$jdk" PATH="$dir/tools/$jdk/bin:$PATH"
CITYWORLD_SELFTEST_PORT=$port CITYWORLD_SELFTEST_TIMEOUT=5400 scripts/selftest.sh > build/selftest-one.log 2>&1
rc=$?
echo "$(basename "$dir") rc=$rc $(grep -a '^>> PASS\|^!!' build/selftest-one.log | head -2 | tr '\n' ' ')"
grep -a "SELFTEST:   -" build/selftest/*.log 2>/dev/null | cut -c1-240 | head -3
for p in $(pgrep java); do [ "$(readlink /proc/$p/cwd 2>/dev/null)" = "$dir/run" ] && kill "$p"; done
exit $rc
