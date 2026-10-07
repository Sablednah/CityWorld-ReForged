#!/usr/bin/env bash
# Build the all vanilla structures add-on jar: packs/all-vanilla-structures/build/cityworld-all-vanilla-structures-<version>.jar
set -euo pipefail
cd "$(dirname "$0")"
version=$(grep -m1 '^version=' src/META-INF/neoforge.mods.toml | cut -d'"' -f2)
mkdir -p build
out="build/cityworld-all-vanilla-structures-$version.jar"
rm -f "$out"
(cd src && zip -qrX "../$out" . -x '*.DS_Store')
cp README.md build/ 2>/dev/null || true
echo "$out"
