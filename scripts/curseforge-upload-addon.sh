#!/usr/bin/env bash
# Upload one CityWorld add-on jar (Neutral Palette, LootLinker, All Vanilla Structures, Biome Palettes) to ITS OWN
# CurseForge project.
#
#   CURSEFORGE_TOKEN=xxx ./scripts/curseforge-upload-addon.sh <jar> <project-id> <display-name> <changelog-file> [release-type]
#
# Normally run by .github/workflows/curseforge-addons.yml when an add-on's GitHub release is published. An add-on
# never goes to CityWorld's own project: CurseForge would serve it as the latest CityWorld (owner, 2026-10-04).
#
# The add-ons are data-only jars, one jar for every line CityWorld ships for, so each upload is ONE file tagged with
# every Minecraft version below and both loaders (1.20.1 is MinecraftForge, the rest NeoForge), and CityWorld as a
# required dependency. No Java tag: a jar with no classes asks nothing of Java. Version IDs are looked up from the API
# every run (CurseForge wants numbers, and they change as versions are added).
#
# API reference: https://support.curseforge.com/en/support/solutions/articles/9000197321
set -euo pipefail

BASE="https://minecraft.curseforge.com"
MC_VERSIONS=(1.20.1 1.21.1 1.21.11 26.1.2 26.2 26.3)   # CityWorld's lines; CurseForge names 26.1 "26.1.2"
LOADERS=(Forge NeoForge)

JAR="${1:?usage: curseforge-upload-addon.sh <jar> <project-id> <display-name> <changelog-file> [release-type]}"
PROJECT_ID="${2:?missing CurseForge project id}"
DISPLAY_NAME="${3:?missing display name}"
CHANGELOG_FILE="${4:?missing changelog file}"
RELEASE_TYPE="${5:-release}"

: "${CURSEFORGE_TOKEN:?set CURSEFORGE_TOKEN (create one at https://legacy.curseforge.com/account/api-tokens)}"
[ -f "$JAR" ] || { echo "!! No such jar: $JAR" >&2; exit 1; }
[ -f "$CHANGELOG_FILE" ] || { echo "!! No such changelog: $CHANGELOG_FILE" >&2; exit 1; }

api() { curl -sS --max-time 120 -H "X-Api-Token: $CURSEFORGE_TOKEN" "$@"; }

VERSIONS_JSON="$(api "$BASE/api/game/versions")"
if ! jq -e 'type == "array"' >/dev/null 2>&1 <<<"$VERSIONS_JSON"; then
    echo "!! Unexpected response from $BASE/api/game/versions — is the token valid?" >&2
    head -c 400 <<<"$VERSIONS_JSON" >&2; echo >&2
    exit 1
fi

idof() { jq -r --arg v "$1" 'map(select(.name == $v)) | .[0].id // empty' <<<"$VERSIONS_JSON"; }

ids=()
for name in "${MC_VERSIONS[@]}" "${LOADERS[@]}" Client Server; do
    id="$(idof "$name")"
    if [ -z "$id" ]; then
        # every one of these is a version CityWorld already ships on CurseForge, so a miss is a real fault
        echo "!! CurseForge has no version tag named '$name'." >&2
        exit 1
    fi
    echo "   $name = $id"
    ids+=("$id")
done
GAME_VERSIONS="[$(IFS=,; echo "${ids[*]}")]"

# CityWorld is required: every add-on is data for CityWorld's own registries and tags. Relations are keyed by SLUG
# (curseforge.com/minecraft/mc-mods/cityworld-reforged).
RELATIONS='{"projects":[{"slug":"cityworld-reforged","type":"requiredDependency"}]}'

METADATA="$(jq -n \
    --rawfile changelog "$CHANGELOG_FILE" \
    --arg displayName "$DISPLAY_NAME" \
    --arg releaseType "$RELEASE_TYPE" \
    --argjson gameVersions "$GAME_VERSIONS" \
    --argjson relations "$RELATIONS" \
    '{changelog: $changelog, changelogType: "markdown", displayName: $displayName,
      releaseType: $releaseType, gameVersions: $gameVersions, relations: $relations}')"

if [ -n "${CURSEFORGE_DEBUG:-}" ]; then
    echo ">> metadata:"
    jq . <<<"$METADATA" | sed 's/^/     /'
fi

echo ">> Uploading $(basename "$JAR") to project $PROJECT_ID as \"$DISPLAY_NAME\" ($RELEASE_TYPE)"
# --form-string, not -F, for the metadata: see curseforge-upload.sh (a ';' or leading '@'/'<' in -F mangles it)
RESPONSE="$(curl -sS --max-time 600 -w '\n%{http_code}' \
    -H "X-Api-Token: $CURSEFORGE_TOKEN" \
    --form-string "metadata=$METADATA" \
    -F "file=@$JAR" \
    "$BASE/api/projects/$PROJECT_ID/upload-file")"

STATUS="$(tail -n1 <<<"$RESPONSE")"
BODY="$(sed '$d' <<<"$RESPONSE")"
if [ "$STATUS" = "200" ]; then
    FILE_ID="$(jq -r '.id // empty' <<<"$BODY" 2>/dev/null || true)"
    echo ">> Uploaded${FILE_ID:+ as file $FILE_ID}"
    echo ">> Moderation runs after this: https://authors.curseforge.com/#/projects/$PROJECT_ID/files"
    exit 0
fi
echo "!! CurseForge rejected the upload (HTTP $STATUS)" >&2
echo "$BODY" >&2
exit 1
