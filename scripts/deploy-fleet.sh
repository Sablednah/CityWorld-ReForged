#!/usr/bin/env bash
# Deploy CityWorld jars to every CurseForge instance that carries one — the right jar for each
# instance's Minecraft version — and stamp each mods/ folder with DEPLOYED-<vX.Y.Z|sha>.
#
# The fleet is self-defining: an instance is "in the fleet" if its mods/ already holds a
# cityworld-*.jar (or a DEPLOYED-* stamp). Add an instance by copying any CityWorld jar in once;
# remove one by deleting the jar and the stamp.
#
# Usage:
#   scripts/deploy-fleet.sh                 # newest built jar per MC version, from master + worktrees
#   scripts/deploy-fleet.sh --version 5.7.1 # exactly that version's jars (must already be built)
#   scripts/deploy-fleet.sh --build         # build all three checkouts first (JDK21 / JDK25)
#   scripts/deploy-fleet.sh --dry-run       # say what would happen, touch nothing
#   scripts/deploy-fleet.sh --only 26.2,Standards   # a subset, by instance folder name
#   scripts/deploy-fleet.sh --stamp foo     # override the DEPLOYED- stamp text
#
# Env overrides: CITYWORLD_INSTANCES (CurseForge Instances dir), CITYWORLD_WORKTREES (dir holding
# the mc26.* worktrees), CITYWORLD_JAR_DIRS (colon-separated extra dirs to search for jars).
#
# A running game locks its jar: the copy fails with "Permission denied". That instance is reported
# as SKIPPED and the rest continue; the exit code is 1 if anything was skipped or failed.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INSTANCES="${CITYWORLD_INSTANCES:-/mnt/c/Users/darre/curseforge/minecraft/Instances}"
WORKTREES="${CITYWORLD_WORKTREES:-$ROOT/../CityWorld-ReForged-worktrees}"

WANT_VERSION=""; DO_BUILD=0; DRY=0; ONLY=""; STAMP_OVERRIDE=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version) WANT_VERSION="$2"; shift 2 ;;
        --build)   DO_BUILD=1; shift ;;
        --dry-run) DRY=1; shift ;;
        --only)    ONLY="$2"; shift 2 ;;
        --stamp)   STAMP_OVERRIDE="$2"; shift 2 ;;
        -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "!! unknown argument: $1" >&2; exit 2 ;;
    esac
done

say()  { echo ">> $*"; }
warn() { echo "!! $*" >&2; }

# ---- checkouts: where jars come from, and how to build them --------------------------------------
# name | dir | jdk
CHECKOUTS=(
    "master|$ROOT|$ROOT/tools/jdk21"
    "mc1.21.1|$WORKTREES/mc1.21.1|$ROOT/tools/jdk21"
    "mc26.1|$WORKTREES/mc26.1|$ROOT/tools/jdk25"
    "mc26.2|$WORKTREES/mc26.2|$ROOT/tools/jdk25"
)

if [ "$DO_BUILD" = 1 ]; then
    for c in "${CHECKOUTS[@]}"; do
        IFS='|' read -r name dir jdk <<<"$c"
        if [ ! -d "$dir" ]; then warn "checkout $name missing at $dir — skipping build"; continue; fi
        if [ ! -x "$jdk/bin/java" ]; then warn "JDK for $name missing at $jdk — skipping build"; continue; fi
        say "Building $name ($dir) with $(basename "$jdk")..."
        if [ "$DRY" = 1 ]; then continue; fi
        ( cd "$dir" && JAVA_HOME="$jdk" PATH="$jdk/bin:$PATH" ./gradlew build --console=plain -q ) \
            || { warn "build FAILED for $name"; exit 1; }
    done
fi

# ---- collect candidate jars -----------------------------------------------------------------------
JAR_DIRS=()
for c in "${CHECKOUTS[@]}"; do IFS='|' read -r _ dir _ <<<"$c"; [ -d "$dir/build/libs" ] && JAR_DIRS+=("$dir/build/libs"); done
if [ -n "${CITYWORLD_JAR_DIRS:-}" ]; then IFS=':' read -ra extra <<<"$CITYWORLD_JAR_DIRS"; JAR_DIRS+=("${extra[@]}"); fi

# jar_for <mcversion> -> path of the jar to deploy, or empty.
# Jar names are cityworld-<ver>+mc<mc>.jar; the instance's version must equal <mc> or extend it
# ("26.2.1" matches "+mc26.2", "26.1.2" matches "+mc26.1.2", nothing matches "+mc26.1" for 26.10).
jar_for() {
    local mc="$1" best="" best_t=0 f t base suffix ver
    for d in "${JAR_DIRS[@]}"; do
        for f in "$d"/cityworld-*+mc*.jar; do
            [ -e "$f" ] || continue
            case "$f" in *-sources.jar|*-javadoc.jar) continue ;; esac
            base="$(basename "$f" .jar)"; suffix="${base##*+mc}"; ver="${base#cityworld-}"; ver="${ver%%+mc*}"
            [ "$mc" = "$suffix" ] || [[ "$mc" == "$suffix".* ]] || continue
            if [ -n "$WANT_VERSION" ]; then
                [ "$ver" = "$WANT_VERSION" ] || continue
            fi
            t=$(stat -c %Y "$f")
            # Without --version: newest by mtime. With it: exact version, newest copy if several dirs hold one.
            if [ -z "$best" ] || [ "$t" -gt "$best_t" ]; then best="$f"; best_t=$t; fi
        done
    done
    echo "$best"
}

manifest_field() { unzip -p "$1" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | awk -v k="$2" -F': ' '$1==k{print $2}'; }

# stamp_for <jar>: vX.Y.Z when the jar is a release build, else its short commit (the ship-loop
# convention: DEPLOYED-<sha|vX.Y.Z>). A release build is one whose Build-Commit is what the vX.Y.Z
# tag points at (master), or a "Bump to X.Y.Z" commit (the version branches are not tagged — each
# releases from its own bump commit). A -dirty build is never a release.
stamp_for() {
    local jar="$1" ver commit tags subject
    [ -n "$STAMP_OVERRIDE" ] && { echo "$STAMP_OVERRIDE"; return; }
    ver="$(manifest_field "$jar" Implementation-Version)"
    commit="$(manifest_field "$jar" Build-Commit)"
    if [ -n "$commit" ] && [ -n "$ver" ]; then
        case "$commit" in *-dirty) echo "$commit"; return ;; esac
        tags="$(git -C "$ROOT" tag --points-at "$commit" 2>/dev/null || true)"
        subject="$(git -C "$ROOT" log -1 --format=%s "$commit" 2>/dev/null || true)"
        if grep -qx "v$ver" <<<"$tags" || [ "$subject" = "Bump to $ver" ]; then echo "v$ver"; return; fi
        echo "$commit"; return
    fi
    echo "v${ver:-unknown}"
}

instance_mc() {
    python3 - "$1/minecraftinstance.json" <<'PY' 2>/dev/null
import json,sys
j=json.load(open(sys.argv[1],encoding='utf-8-sig'))
print(j.get('baseModLoader',{}).get('minecraftVersion') or j.get('gameVersion') or '')
PY
}

# ---- the fleet --------------------------------------------------------------------------------------
if [ ! -d "$INSTANCES" ]; then warn "instances dir not found: $INSTANCES"; exit 2; fi
FLEET=()
NODEPLOY=()
for d in "$INSTANCES"/*/; do
    d="${d%/}"; m="$d/mods"; [ -d "$m" ] || continue
    if compgen -G "$m/cityworld-*.jar" >/dev/null || compgen -G "$m/DEPLOYED-*" >/dev/null; then
        if [ -n "$ONLY" ]; then
            case ",$ONLY," in *",$(basename "$d"),"*) ;; *) continue ;; esac
        fi
        # Family convention (Chronicler, Cast, CityWorld): an instance whose ROOT holds
        # .sablecraft-no-deploy is never deployed to, even when --only names it. It is still LISTED, as a
        # skip: an opted-out instance that vanishes from the table is indistinguishable from one the fleet
        # scan missed, and this fleet is self-defining, so a silent omission is the one failure mode that
        # would go unnoticed. The case it exists for is a modpack instance that must hold only released
        # CurseForge jars — a dev jar there looks exactly like a released one and becomes what ships.
        if [ -e "$d/.sablecraft-no-deploy" ]; then NODEPLOY+=("$d"); continue; fi
        FLEET+=("$d")
    fi
done
if [ ${#FLEET[@]} -eq 0 ]; then
    if [ ${#NODEPLOY[@]} -gt 0 ]; then
        warn "every CityWorld instance under $INSTANCES is opted out with .sablecraft-no-deploy"
        for d in "${NODEPLOY[@]}"; do warn "  opted out: $(basename "$d")"; done
        exit 0
    fi
    warn "no instances carry CityWorld under $INSTANCES"; exit 2
fi

say "Fleet: ${#FLEET[@]} instance(s)$( [ ${#NODEPLOY[@]} -gt 0 ] && echo ", ${#NODEPLOY[@]} opted out")$( [ "$DRY" = 1 ] && echo ' (DRY RUN)')"
printf '%-28s %-8s %-36s %-12s %s\n' INSTANCE MC JAR STAMP RESULT
rc=0
# Listed, not hidden — and deliberately NOT an rc=1 skip: opting out is the instance's owner saying no,
# which is a success, unlike a jar locked by a running game.
for d in ${NODEPLOY[@]+"${NODEPLOY[@]}"}; do
    printf '%-28s %-8s %-36s %-12s %s\n' "$(basename "$d")" "-" "-" "-" "SKIPPED: .sablecraft-no-deploy"
done
for inst in "${FLEET[@]}"; do
    name="$(basename "$inst")"; mods="$inst/mods"
    mc="$(instance_mc "$inst")"
    if [ -z "$mc" ]; then printf '%-28s %-8s %-36s %-12s %s\n' "$name" "?" "-" "-" "FAILED: no minecraftinstance.json"; rc=1; continue; fi
    jar="$(jar_for "$mc")"
    if [ -z "$jar" ]; then printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "-" "-" "FAILED: no jar built for MC $mc${WANT_VERSION:+ at $WANT_VERSION}"; rc=1; continue; fi
    stamp="$(stamp_for "$jar")"
    jarname="$(basename "$jar")"

    # Already there and stamped identically? Compare bytes, not names — a rebuilt jar keeps its name.
    if [ -f "$mods/$jarname" ] && cmp -s "$jar" "$mods/$jarname" && [ -e "$mods/DEPLOYED-$stamp" ]; then
        printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "$jarname" "$stamp" "up to date"; continue
    fi
    if [ "$DRY" = 1 ]; then
        printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "$jarname" "$stamp" "would deploy"; continue
    fi

    # Copy to a temp name first, then swap: a locked (running) game fails on the rm, and we then
    # have not left the instance with no jar at all.
    tmp="$mods/.$jarname.tmp"
    if ! cp "$jar" "$tmp" 2>/dev/null; then
        rm -f "$tmp" 2>/dev/null
        printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "$jarname" "$stamp" "SKIPPED: cannot write mods/ (game running?)"; rc=1; continue
    fi
    locked=0
    for old in "$mods"/cityworld-*.jar; do
        [ -e "$old" ] || continue
        rm -f "$old" 2>/dev/null || { locked=1; break; }
    done
    if [ "$locked" = 1 ]; then
        rm -f "$tmp"
        printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "$jarname" "$stamp" "SKIPPED: jar locked (game running) — left as was"; rc=1; continue
    fi
    mv -f "$tmp" "$mods/$jarname"
    rm -f "$mods"/DEPLOYED-* 2>/dev/null
    : > "$mods/DEPLOYED-$stamp"
    printf '%-28s %-8s %-36s %-12s %s\n' "$name" "$mc" "$jarname" "$stamp" "deployed"
done
[ "$rc" = 0 ] || warn "some instances were skipped or failed (see above)"
exit $rc
