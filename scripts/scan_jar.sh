#!/usr/bin/env bash
# The shipped-jar gate: count server-shutdown call sites, Timings classes and debug classes in a built jar.
#   scripts/scan_jar.sh <jar> [<jar> ...]
# All three must read 0 for a release (CLAUDE.md: CurseForge rejected 5.7.0/5.8.0 over server.halt()).
# ⚠ Prove it on a positive first — a jar holding a class that calls System.exit and a Timings class must read
# 1/1 — because a detector that has never fired is worth nothing. One javap over ALL classes, not one per class.
# m_7570_ is halt()'s SRG name, for the 1.20.1 Forge jar.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for jar in "$@"; do
    # the path is resolved BEFORE the cd, and an unreadable jar is an error: the first version of this script
    # took a relative path, failed to open it from the temp folder, and still printed "halt/exit=0"
    full="$(realpath "$jar" 2>/dev/null)"
    if [ ! -f "$full" ]; then echo "$jar: NOT FOUND" >&2; exit 2; fi
    d=$(mktemp -d); ( cd "$d" && unzip -q "$full" || { echo "$jar: could not unzip" >&2; exit 2; }
      classes=$(find . -name '*.class' | sed 's|^\./||; s|\.class$||' | tr / .)
      [ -n "$classes" ] || { echo "$jar: no classes found" >&2; exit 2; }
      n=$("$ROOT/tools/jdk21/bin/javap" -p -c -cp . $classes 2>/dev/null | grep -cE '\.halt:|System\.exit:|m_7570_')
      t=$(find . -name '*Timings*' | wc -l); g=$(find . -path '*debug*' | wc -l)
      echo "$(basename "$jar"): halt/exit=$n Timings=$t debug=$g ($(echo "$classes" | wc -w) classes)" ) || { rm -rf "$d"; exit 2; }
    rm -rf "$d"
done
