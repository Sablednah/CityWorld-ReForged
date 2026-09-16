#!/usr/bin/env bash
# Build CityWorld and copy the jar into a NeoForge test instance's mods/ folder,
# then launch that instance (e.g. from CurseForge) to see the mod live.
#
# Usage:   ./deploy.sh
# Override the target instance dir:
#          CITYWORLD_INSTANCE="/path/to/instance" ./deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$ROOT/tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

# Target NeoForge instance (a NeoForge 1.21.11 CurseForge instance).
# Override with CITYWORLD_INSTANCE=/path/to/instance ./deploy.sh
INSTANCE="${CITYWORLD_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/CityWork-ReForged}"
MODS="$INSTANCE/mods"

# Family convention (Chronicler, Cast, CityWorld): an instance whose ROOT holds .sablecraft-no-deploy is
# never deployed to — not even when it is named explicitly through CITYWORLD_INSTANCE. Naming it is exactly
# what someone does by habit, so "explicit wins" would defeat the marker's only purpose. A modpack instance
# that must contain none but released CurseForge jars is the case this exists for: a dev jar there looks
# identical to a released one and silently becomes what the pack ships.
if [ -e "$INSTANCE/.sablecraft-no-deploy" ]; then
    echo "!! Refusing to deploy: $INSTANCE/.sablecraft-no-deploy" >&2
    echo "!! Nothing was built and nothing was copied." >&2
    [ -s "$INSTANCE/.sablecraft-no-deploy" ] && sed 's/^/!!   /' "$INSTANCE/.sablecraft-no-deploy" >&2
    echo "!! Remove that file if this instance really should take dev jars." >&2
    exit 3
fi

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
