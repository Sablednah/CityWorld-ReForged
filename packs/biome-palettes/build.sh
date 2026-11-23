#!/usr/bin/env bash
# Build the Biome Palettes add-on jar: packs/biome-palettes/build/cityworld-biome-palettes-<version>.jar
# (python3 gen.py first if the tables in it changed). With --test, a jar that also loads beside a CityWorld
# build that has not had its version bumped to 7.3 yet: build/cityworld-biome-palettes-<version>-test72.jar.
set -euo pipefail
cd "$(dirname "$0")"
version=$(grep -m1 '^version=' src/META-INF/neoforge.mods.toml | cut -d'"' -f2)
mkdir -p build
if [ "${1:-}" = "--test" ]; then
  out="build/cityworld-biome-palettes-$version-test72.jar"
  rm -rf build/test-src && cp -r src build/test-src
  sed -i 's/versionRange="\[7\.3,)"/versionRange="[7.2,)"/' build/test-src/META-INF/neoforge.mods.toml build/test-src/META-INF/mods.toml
  rm -f "$out"
  (cd build/test-src && zip -qrX "../../$out" . -x '*.DS_Store')
  rm -rf build/test-src
else
  out="build/cityworld-biome-palettes-$version.jar"
  rm -f "$out"
  (cd src && zip -qrX "../$out" . -x '*.DS_Store')
fi
cp README.md build/ 2>/dev/null || true
echo "$out"
