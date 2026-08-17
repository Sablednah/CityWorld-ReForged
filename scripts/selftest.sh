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

# A fresh world every time: existing chunks never regenerate, so a stale one would test nothing.
rm -rf "$ROOT/run/world"

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

echo ">> Running (generates a world, verifies, then halts)..."
LOG="$ROOT/build/selftest/$VERSION.log"
set +e
"$ROOT/gradlew" runSelfTest --console=plain > "$LOG" 2>&1
GRADLE_STATUS=$?
set -e

if [ -f "$ROOT/run/cityworld-selftest.json" ]; then
    cp "$ROOT/run/cityworld-selftest.json" "$REPORTS/$VERSION.json"
fi

echo
grep -E "SELFTEST:" "$LOG" | sed 's/.*SELFTEST:/  /' || true
echo

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
