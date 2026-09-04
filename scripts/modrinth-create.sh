#!/usr/bin/env bash
# Create the Modrinth project, set its icon, and upload the gallery.
#
# Adapted from ZombieMod's script of the same name — the API handling is identical and hard-won, so
# it is copied rather than rewritten. Only the project's own facts differ.
#
#   MODRINTH_TOKEN=xxx ./scripts/modrinth-create.sh
#
# Normally run for you by .github/workflows/modrinth.yml via "Run workflow" -> create-project, so
# the token never has to leave GitHub's secret store. Runnable by hand with a PAT that has the
# PROJECT_CREATE and PROJECT_WRITE scopes.
#
# Safe to re-run. If the project already exists it skips creation and still refreshes the icon and
# any gallery image that is missing, so a failed run halfway through is fixed by running it again.
#
# The project lands as a DRAFT - private, not in search. Nothing is public until a version exists
# and ./scripts/modrinth-submit.sh sends it to moderation, which is a deliberate second step.
#
# Copy comes from RELEASE.md, which is the single source for every store string. If a tagline
# changes, change it there first.
#
# API reference: https://docs.modrinth.com/api/  (spec: https://docs.modrinth.com/openapi.yaml)
set -euo pipefail

API="https://api.modrinth.com/v2"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SLUG="${MODRINTH_SLUG:-cityworld-reforged}"
ICON="$HERE/docs/modrinth-icon.png"
BODY_FILE="$HERE/CURSEFORGE.md"

: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN (create one at https://modrinth.com/settings/pats with the PROJECT_CREATE and PROJECT_WRITE scopes)}"

# Modrinth asks for a descriptive User-Agent and rate-limits anonymous-looking clients harder.
UA="Sablednah/CityWorld-ReForged (sablecraft.co.uk)"
# Note: no "Bearer" prefix. Modrinth takes the raw token as the Authorization header.
AUTH="Authorization: $MODRINTH_TOKEN"

[ -f "$ICON" ]      || { echo "!! No icon at $ICON - regenerate it, see RELEASE.md" >&2; exit 1; }
[ -f "$BODY_FILE" ] || { echo "!! No description at $BODY_FILE" >&2; exit 1; }

# The icon must be square and under 256 KiB. Checked here rather than discovered as a 400, because
# the failure reads as an auth problem otherwise.
ICON_BYTES="$(wc -c < "$ICON")"
[ "$ICON_BYTES" -le 262144 ] || {
    echo "!! $ICON is $ICON_BYTES bytes; Modrinth's icon limit is 262144 (256 KiB)." >&2
    echo "!! Re-quantise it - the generator is in RELEASE.md." >&2; exit 1; }

api() {  # api <method> <path> [curl args...] -> body on stdout, non-2xx is fatal
    local method="$1" path="$2"; shift 2
    local out
    out="$(curl -sS --max-time 300 -w '\n%{http_code}' -X "$method" \
        -H "$AUTH" -H "User-Agent: $UA" "$@" "$API$path")"
    local status body
    status="$(tail -n1 <<<"$out")"
    body="$(sed '$d' <<<"$out")"
    case "$status" in
        2*) printf '%s' "$body"; return 0 ;;
        *)  echo "!! $method $path failed (HTTP $status)" >&2
            echo "$body" >&2
            [ "$status" = 401 ] && echo "!! 401 means the token was rejected. Note Modrinth takes the raw token with NO 'Bearer ' prefix, and the PAT needs the PROJECT_CREATE / PROJECT_WRITE scopes." >&2
            return 1 ;;
    esac
}

# --- does it exist already? ---------------------------------------------------------------------
echo ">> Checking for an existing project at /$SLUG"
EXISTING="$(curl -sS -o /dev/null -w '%{http_code}' -H "$AUTH" -H "User-Agent: $UA" "$API/project/$SLUG")"

# The metadata, built once and used either way: POSTed to create the project, or PATCHed onto one
# that already exists so a re-run picks up an edited CURSEFORGE.md. Without that, fixing a typo in
# the description would mean editing it by hand on the website, and the file would stop being the
# source of truth.
#
# client_side / server_side are marked deprecated in favour of `environment`, but `environment` is a
# *version* field in v2, not a project one, and the deprecated pair is still required here.
# Server required + client optional is the exact truth: world generation happens server-side, so a
# player joining a CityWorld server needs nothing. A client that HAS the mod additionally gets the
# Customize screen when creating a single-player world. Marking the client unsupported would be wrong
# (single-player is a first-class case here), and marking it required would turn away every player on
# somebody else's server.
DATA="$(BODY="$BODY_FILE" SLUG="$SLUG" python3 -c '
import json, os, sys
# The one-liner from RELEASE.md. Modrinth caps this at 256 characters; checked rather than
# discovered as a validation error naming the field but not the limit.
summary = ("Procedurally generated cities as a world type — roads, skyscrapers, sewers, mines, "
           "farms and ruins, across thirteen world styles.")
if len(summary) > 256:
    sys.exit("summary is %d characters; Modrinth allows 256" % len(summary))
print(json.dumps({
  "slug": os.environ["SLUG"],
  "title": "CityWorld ReForged",
  "description": summary,
  "body": open(os.environ["BODY"], encoding="utf-8").read(),
  "categories": ["worldgen", "adventure", "game-mechanics"],
  "client_side": "optional",
  "server_side": "required",
  # ⚠ GPL-3.0-only, and not negotiable: upstream CityWorld is GPL-3, so this port is a derivative
  # work. See the licence note in CLAUDE.md before anyone "tidies" this to something permissive.
  "license_id": "GPL-3.0-only",
  "project_type": "mod",
  "issues_url": "https://github.com/Sablednah/CityWorld-ReForged/issues",
  "source_url": "https://github.com/Sablednah/CityWorld-ReForged",
  "wiki_url": "https://sablecraft.co.uk/cityworld-reforged/",
  # `initial_versions` and `is_draft` are marked DEPRECATED in the published spec, and the live v2
  # endpoint still REQUIRES them: leaving initial_versions out gives
  #   400 invalid_input "Error while parsing JSON: missing field `initial_versions`"
  # which reads like malformed JSON rather than a missing field. Send them empty and upload the
  # versions afterwards through /version, which is what the deprecation is steering you towards.
  "initial_versions": [],
  "is_draft": True,
}))')"

if [ "$EXISTING" = "200" ]; then
    echo "   Already exists - updating its description from $(basename "$BODY_FILE")."
    # PATCH takes JSON, not multipart, and rejects the fields that are create-only.
    PATCH_DATA="$(python3 -c '
import json, sys
d = json.load(sys.stdin)
# Create-only fields: PATCH rejects them.
for k in ("slug", "project_type", "client_side", "server_side",
          "initial_versions", "is_draft", "gallery_items"):
    d.pop(k, None)
print(json.dumps(d))' <<<"$DATA")"
    api PATCH "/project/$SLUG" -H "Content-Type: application/json" --data-binary "$PATCH_DATA" > /dev/null
else
    echo ">> Creating the project"
    # --form-string, not -F: curl gives ';', a leading '@' and a leading '<' special meaning inside
    # an -F value, and the description body is full-page Markdown that contains all three.
    api POST /project --form-string "data=$DATA" > /dev/null
    echo "   Created as a draft."
fi

# --- icon ---------------------------------------------------------------------------------------
# A dedicated endpoint rather than the `icon` part of the create call, so re-running this script
# updates the artwork on a project that already exists.
echo ">> Uploading the icon ($ICON_BYTES bytes)"
api PATCH "/project/$SLUG/icon?ext=png" \
    -H "Content-Type: image/png" --data-binary "@$ICON" > /dev/null

# --- gallery ------------------------------------------------------------------------------------
# Order and captions from RELEASE.md. The first two do the persuading, so they lead. Modrinth shows
# the featured image on the project card, so `giant.png` is featured: it is the only clean daylight
# shot with no HUD.
#
# file<TAB>featured<TAB>title<TAB>description
GALLERY=$(cat <<'ENTRIES'
skyline.jpg	true	The skyline	A generated downtown at dusk — towers, lit windows, and the road grid running out to the suburbs.
street-level.jpg	false	Street level	Pavements, crossings, street lights and parked cars, at the scale you actually play at.
inside.jpg	false	Buildings are furnished	Interiors are generated too: floors, lighting, furniture and loot.
suburbs.jpg	false	Out to the suburbs	Density falls off with distance — houses, gardens and farmland past the city edge.
bridge.jpg	false	Bridges and water	Roads carry across rivers and inlets rather than stopping at them.
tunnel.jpg	false	And under the hills	Road tunnels, lit and signed.
mine-tunnel.jpg	false	Mines below	Working mine levels with lifts, rails and camps.
lush-cave.jpg	false	Cave biomes	Lush, dripstone and deep dark caves sit under the city, with their own decoration.
vault.jpg	false	The vault	An APOCALYPSE-only bunker complex behind a blast door.
zoo.jpg	false	Zoos and parks	Themed pens and animals in park districts.
biodome.jpg	false	Biodomes	Glass domes holding a slice of another biome.
oil-platform.jpg	false	Out at sea	Oil platforms offshore.
biomes.jpg	false	Biomes, including modded ones	CityWorld's own climate map, extended by any TerraBlender biome mod you install.
peak.jpg	false	Peaks and ice	Terrain runs from ocean floor to iced summit.
apocalypse.jpg	false	APOCALYPSE	The same world gone to ruin: decayed roads and buildings, overgrowth, and things that hunt you.
apocalypse-street.jpg	false	A street after the end	Cracked tarmac, fallen walls, and plants taking the pavement back.
apocalypse-highrise.jpg	false	Ruined high-rise	Floors open to the sky.
apocalypse-plaza.jpg	false	What is left of a plaza	Civic architecture, weathered and broken.
apocalypse-ruins.jpg	false	Ruins	Enough structure left to read what it was.
schematics.jpg	false	Drop in your own buildings	Your schematics get salted into the cities alongside the generated ones.
lava-pool.jpg	false	Underground hazards	Lava fields and open shafts, down where the mines run.
wait-for-it-to-blow-over.jpg	false	Shelter	Sometimes you just wait for it to blow over.
ENTRIES
)

# Whatever is already up, so a re-run adds only what is missing. Modrinth rejects a duplicate image
# with a 400 that does not say "duplicate", so this is worth doing rather than catching.
HAVE="$(curl -sS -H "$AUTH" -H "User-Agent: $UA" "$API/project/$SLUG" \
    | python3 -c 'import json,sys
try: print("\n".join(g.get("title") or "" for g in (json.load(sys.stdin).get("gallery") or [])))
except Exception: pass')"

ORDER=0
while IFS=$'\t' read -r FILE FEATURED TITLE DESC; do
    [ -n "${FILE:-}" ] || continue
    ORDER=$((ORDER + 1))
    IMG="$HERE/screengrabs/$FILE"
    [ -f "$IMG" ] || { echo "   !! missing $IMG - skipping" >&2; continue; }
    if grep -Fxq "$TITLE" <<<"$HAVE"; then
        echo "   $ORDER. $FILE - already uploaded, skipping"
        continue
    fi
    echo "   $ORDER. $FILE  ($TITLE)"
    # Titles and captions are query parameters, so they must be percent-encoded - several contain
    # spaces, colons and commas.
    QS="$(F="$FEATURED" T="$TITLE" D="$DESC" O="$ORDER" python3 -c '
import os, urllib.parse
print(urllib.parse.urlencode({"ext": "png", "featured": os.environ["F"],
                              "title": os.environ["T"], "description": os.environ["D"],
                              "ordering": os.environ["O"]}))')"
    api POST "/project/$SLUG/gallery?$QS" \
        -H "Content-Type: image/png" --data-binary "@$IMG" > /dev/null
done <<<"$GALLERY"

echo
echo ">> Done. The project is a DRAFT and is not public yet:"
echo "   https://modrinth.com/mod/$SLUG"
echo ">> Next: upload a version (scripts/modrinth-upload.sh), then submit for review"
echo "   (scripts/modrinth-submit.sh). Modrinth will not accept a project with no files."
