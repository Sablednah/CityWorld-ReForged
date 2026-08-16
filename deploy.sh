#!/usr/bin/env bash
# Build CityWorld and copy the jar into a NeoForge test instance's mods/ folder,
# then launch that instance (e.g. from CurseForge) to see the mod live.
#
# Usage:   ./deploy.sh
# Override the target instance dir:
#          CITYWORLD_INSTANCE="/path/to/instance" ./deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# Minecraft 1.21.x builds on JDK 21; 26.1+ needs JDK 25. Prefer the newest we have,
# and let JAVA_HOME from the environment win if it is already set.
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in jdk25 jdk21; do
        if [ -x "$ROOT/tools/$candidate/bin/java" ]; then
            export JAVA_HOME="$ROOT/tools/$candidate"
            break
        fi
    done
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Target NeoForge instance (a NeoForge 1.21.11 CurseForge instance).
# Override with CITYWORLD_INSTANCE=/path/to/instance ./deploy.sh
INSTANCE="${CITYWORLD_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/CityWork-ReForged}"
MODS="$INSTANCE/mods"

echo ">> Building CityWorld..."
"$ROOT/gradlew" build --console=plain

if [ ! -d "$MODS" ]; then
    echo "!! Instance mods folder not found: $MODS" >&2
    echo "!! Create a NeoForge 1.21.11 instance (default name 'CityWork-ReForged')," >&2
    echo "!! or run: CITYWORLD_INSTANCE=\"/path/to/instance\" ./deploy.sh" >&2
    exit 1
fi

echo ">> Removing previous CityWorld jars from the instance..."
rm -f "$MODS"/cityworld-*.jar

JAR="$(ls -t "$ROOT"/build/libs/cityworld-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
if [ -z "$JAR" ]; then
    echo "!! No built jar found in build/libs" >&2
    exit 1
fi

cp "$JAR" "$MODS/"
echo ">> Deployed: $(basename "$JAR")"
echo ">> Launch the '$(basename "$INSTANCE")' instance to test."
