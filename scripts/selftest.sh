#!/usr/bin/env bash
# Headless CityWorld verification, for maintaining several Minecraft versions at once.
#
#   ./scripts/selftest.sh              run the self-test for the version this checkout targets
#   ./scripts/selftest.sh --compare    compare every report collected so far
#
# Minecraft ships quarterly now, so CityWorld is built against several versions and hand-testing each
# does not scale. This runs a real dedicated server on a fixed seed, generates a CityWorld world, and
# checks: the generator is actually installed, the planner produces a full spread of contexts and
# lots, decoration writes real blocks, and sign text survives on both faces (the access-transformer
# canary). See src/.../selftest/CityWorldSelfTest.java.
#
# Typical cross-version run, one branch per version:
#     git checkout master  && ./scripts/selftest.sh     # 1.21.11
#     git checkout mc26.1  && ./scripts/selftest.sh
#     git checkout mc26.2  && ./scripts/selftest.sh
#     git checkout mc26.3  && ./scripts/selftest.sh
#     ./scripts/selftest.sh --compare
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORTS="$ROOT/build/selftest"
# The plan hash is only comparable if every version plans the same world, so pin the seed.
SEED="8675309"

mc_version() {
    grep -E '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '[:space:]'
}


compare_reports() {
    if ! ls "$REPORTS"/*.json >/dev/null 2>&1; then
        echo "!! No reports in $REPORTS — run the self-test on at least two versions first." >&2
        exit 1
    fi
    echo ">> Comparing plan hashes across versions"
    local failed=0
    # Planning never touches the block registry, so a given seed must yield an identical plan on
    # every Minecraft version. Materials are excluded from the hash — those legitimately widen as
    # newer versions add blocks to the palette tags.
    for style in MODERN APOCALYPSE CLASSIC; do
        local line hashes
        line=""
        hashes=""
        for report in "$REPORTS"/*.json; do
            local version hash
            version="$(basename "$report" .json)"
            hash="$(grep -o "\"plan.$style.hash\": \"[^\"]*\"" "$report" | cut -d'"' -f4)"
            line+="    $version: ${hash:-MISSING}\n"
            hashes+="${hash:-MISSING}\n"
        done
        local distinct
        distinct="$(printf "%b" "$hashes" | sort -u | grep -c .)"
        if [ "$distinct" -eq 1 ]; then
            echo "  OK   $style — identical plan on every version"
        else
            echo "  FAIL $style — versions disagree on the city plan:"
            printf "%b" "$line"
            failed=1
        fi
    done
    if [ "$failed" -ne 0 ]; then
        echo
        echo "!! A differing plan hash means a code change altered worldgen on one version only."
        echo "!! Materials are NOT part of this hash, so a wider block palette is not the cause."
        exit 1
    fi
    echo ">> All versions agree."
}

# --compare only reads the JSON reports, so it must run before the JDK is chosen: on CI it runs in
# a separate job that builds nothing, and demanding a JDK there would fail before doing any work.
if [ "${1:-}" = "--compare" ]; then
    compare_reports
    exit 0
fi

# The JDK follows the target: Minecraft 26.x ships the Java 25 runtime, the 1.21 line Java 21.
# Picking by hand is the sort of thing you get wrong once per version, so derive it.
if [ -z "${JAVA_HOME:-}" ]; then
    case "$(mc_version)" in
        # 1.20.1 is the Forge line and ships Java 17; the 1.21 line ships 21. A bare 1.* glob would
        # hand that branch a JDK it cannot build with.
        1.20.*) wanted="jdk17" ;;
        1.*) wanted="jdk21" ;;
        *)   wanted="jdk25" ;;
    esac
    if [ -x "$ROOT/tools/$wanted/bin/java" ]; then
        export JAVA_HOME="$ROOT/tools/$wanted"
    else
        echo "!! Minecraft $(mc_version) needs $ROOT/tools/$wanted, which is missing." >&2
        exit 1
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

VERSION="$(mc_version)"
echo ">> CityWorld self-test — Minecraft $VERSION"

mkdir -p "$REPORTS" "$ROOT/run"

# Accept Mojang's EULA for this headless test server. The dedicated server refuses to start without
# it, and a fresh checkout has no run/ directory at all — which is why CI failed on the 1.20.1 Forge
# line while passing everywhere else: NeoForge's dev runServer writes this itself, the legacyforge
# path does not. Writing it here makes a clean checkout work on every line.
printf 'eula=true\n' > "$ROOT/run/eula.txt"

# A fresh world every time: existing chunks never regenerate, so a stale one would test nothing.
rm -rf "$ROOT/run/world"

# And a fresh report. Without this, a run that dies before writing one leaves the PREVIOUS run's file
# in place, which is then copied out under THIS version's name — so a failed 1.21.11 run publishes
# 26.2's numbers and the comparison silently agrees with itself. Observed, not hypothetical.
rm -f "$ROOT/run/cityworld-selftest.json"

PROPS="$ROOT/run/server.properties"
touch "$PROPS"
# One code path, deliberately. This used to sed when the key existed and append when it didn't, and
# the two disagreed about backslashes: the append wrote 'cityworld\\:city' where sed wrote
# 'cityworld\:city'. On a developer's machine the key always existed, so it always took the sed path
# and worked; on a fresh checkout (CI) it took the append path, the level type failed to parse, and
# the server fell back to VANILLA worldgen — generating a perfectly normal-looking world that was
# not CityWorld at all. Rewriting the file avoids the escaping question entirely.
set_prop() {
    local key="$1" value="$2"
    { grep -v "^$key=" "$PROPS" || true; } > "$PROPS.tmp"
    echo "$key=$value" >> "$PROPS.tmp"
    mv "$PROPS.tmp" "$PROPS"
}
# No backslash needed: java.util.Properties splits on the FIRST unescaped '=' or ':', which is the
# '=' after the key, so everything past it — colon included — is the value.
set_prop "level-type" "cityworld:city"
set_prop "level-seed" "$SEED"
set_prop "online-mode" "false"
# Off the default port. The harness never accepts a connection, but binding 25565 makes it collide
# with any dev server already running — including one from a *different* mod in the same workspace.
# The collision surfaces as "Failed to initialize server" plus an NPE in overworld() on shutdown,
# which reads exactly like a CityWorld fault and is not one (see CLAUDE.md). Override with
# CITYWORLD_SELFTEST_PORT if 25599 is taken too.
set_prop "server-port" "${CITYWORLD_SELFTEST_PORT:-25599}"

echo ">> Running (generates a world, verifies, then this script stops it)..."
LOG="$ROOT/build/selftest/$VERSION.log"
: > "$LOG"
# Ending the run is THIS SCRIPT'S job now. The harness used to call server.halt() when it finished;
# it must not any more, because CurseForge rejected 5.7.0 and 5.8.0 with "Please remove any function
# that shuts the Minecraft server down" (see CityWorldSelfTest). Nothing in the shipped jar may stop a
# server, so the decision moved out here, to the thing that started it.
#
# setsid puts gradle and every JVM it spawns into their own process group, so one kill on the negative
# PGID takes the whole tree down. That detail is load-bearing: gradle runs the server as a CHILD, so
# killing the gradle PID alone orphans a server that goes on holding run/world/session.lock and port
# 25599 — and the NEXT run then dies with "already locked" or "Address already in use", which reads
# exactly like a CityWorld fault and is not one (CLAUDE.md). Equally: never match a pkill PATTERN here.
# A pattern broad enough to catch the server also matches this script's own command line, and kills it.
SELFTEST_TIMEOUT="${CITYWORLD_SELFTEST_TIMEOUT:-1800}"
set +e
# `set -m` (job control) puts each background job in its OWN process group whose PGID equals the job's
# PID — so the group to kill is simply $!, with nothing to look up and so nothing to get wrong.
#
# The first attempt used `setsid ... &` and then read the PGID back with `ps -o pgid=`. That is a trap:
# $! is SETSID's pid, setsid exits the moment it forks, and the lookup then resolved to THIS SCRIPT'S
# group — so the kill terminated the script itself ("Terminated", exit 143, measured 2026-09-16). It is
# the pkill-matches-your-own-command-line mistake (CLAUDE.md) wearing a different hat, so the guard
# below is belt and braces: never signal our own group, whatever the arithmetic says.
set -m
"$ROOT/gradlew" runSelfTest --console=plain > "$LOG" 2>&1 &
GRADLE_PID=$!
set +m
OWN_PGID="$(ps -o pgid= -p $$ 2>/dev/null | tr -d ' ')"
end_run() {
    [ -n "$GRADLE_PID" ] || return 0
    if [ -n "$OWN_PGID" ] && [ "$GRADLE_PID" = "$OWN_PGID" ]; then
        echo "!! Refusing to kill process group $GRADLE_PID — it is this script's own." >&2
        return 0
    fi
    kill -TERM "-$GRADLE_PID" 2>/dev/null
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        kill -0 "-$GRADLE_PID" 2>/dev/null || return 0
        sleep 1
    done
    kill -KILL "-$GRADLE_PID" 2>/dev/null
}
waited=0
while :; do
    # The harness logs this whether it passed, failed, or threw — writeReport and the final line run
    # outside the try. So this is the one marker that means "the checks are over", and PASS/FAIL is
    # read from the log afterwards exactly as before.
    if grep -q "SELFTEST: complete" "$LOG" 2>/dev/null; then
        end_run
        wait "$GRADLE_PID" 2>/dev/null
        GRADLE_STATUS=0
        break
    fi
    # Gradle exited by itself: the server crashed, or never started. Keep its status for the
    # "harness never ran" branch below, which reports it.
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        wait "$GRADLE_PID" 2>/dev/null
        GRADLE_STATUS=$?
        break
    fi
    if [ "$waited" -ge "$SELFTEST_TIMEOUT" ]; then
        echo "!! Timed out after ${SELFTEST_TIMEOUT}s waiting for 'SELFTEST: complete'." >&2
        echo "!! Killing the run; raise CITYWORLD_SELFTEST_TIMEOUT if the machine is just slow." >&2
        end_run
        GRADLE_STATUS=124
        break
    fi
    sleep 2
    waited=$((waited + 2))
done
set -e

if [ -f "$ROOT/run/cityworld-selftest.json" ]; then
    cp "$ROOT/run/cityworld-selftest.json" "$REPORTS/$VERSION.json"
fi

echo
grep -E "SELFTEST:" "$LOG" | sed 's/.*SELFTEST:/  /' || true
echo

# Block-entity placeholders: a chunk under generation writes a DUMMY entity tag for every block
# whose type can carry one; a multi-block piece backs only its origin, so every other cell logged
# "Tried to load a block entity ... but failed" (195 lines in the owner's log). The placer drops
# those placeholders now; count the warnings so a regression is measured, not spotted in play.
ORPHANED_BE="$(grep -c "Tried to load a block entity" "$LOG" || true)"
echo "   block-entity load warnings in the server log: $ORPHANED_BE"
if [ "${ORPHANED_BE:-0}" -ge 10 ]; then
    echo "!! FAIL — $ORPHANED_BE 'Tried to load a block entity' warnings: multi-block parts are leaving" >&2
    echo "!! DUMMY block-entity tags behind (see SupportBlocks.dropPlaceholderBlockEntity)." >&2
    exit 1
fi

# Datapack parse failures: an element that fails to parse is logged as ONE error line and then simply
# does not exist — no crash, no empty-pool warning, just a chest that silently carries no loot table.
# Seven CityWorld tables were unparseable on 1.20.1 from 5.11.0 until 2026-09-21: they referenced
# another table with 1.21's "value" spelling where 1.20.1's LootTableReference reads "name". It sat in
# plain sight in every run log while ~88 checks looked straight past it. Any count above zero is a bug.
PARSE_FAILS="$(grep -c "Couldn't parse element" "$LOG" || true)"
echo "   datapack elements that failed to parse: $PARSE_FAILS"
if [ "${PARSE_FAILS:-0}" -ge 1 ]; then
    echo "!! FAIL — $PARSE_FAILS datapack element(s) failed to parse, so the objects they define do not" >&2
    echo "!! exist at runtime (a chest keeps no table, a tag resolves empty). Offenders:" >&2
    grep "Couldn't parse element" "$LOG" | sed "s/.*Couldn't parse element/     /" | sort -u | head -10 >&2
    exit 1
fi

if grep -q "SELFTEST: PASS" "$LOG"; then
    echo ">> PASS — Minecraft $VERSION. Report: $REPORTS/$VERSION.json"
    echo ">> Run './scripts/selftest.sh --compare' once other versions have been run."
    exit 0
fi

echo "!! FAIL — Minecraft $VERSION. Full log: $LOG" >&2
# A crash before the harness ran leaves no SELFTEST lines at all; say so rather than implying
# the checks ran and failed.
if ! grep -q "SELFTEST:" "$LOG"; then
    echo "!! The harness never ran — the server failed to start (gradle exit $GRADLE_STATUS)." >&2
    tail -25 "$LOG" >&2
fi
exit 1
