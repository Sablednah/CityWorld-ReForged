#!/usr/bin/env bash
# Upload one built jar to the CurseForge project.
#
#   CURSEFORGE_TOKEN=xxx CURSEFORGE_PROJECT_ID=123456 \
#     ./scripts/curseforge-upload.sh <jar> <minecraft-version> <changelog-file> [release-type]
#
# Normally run for you by .github/workflows/curseforge.yml when a GitHub release is published, so
# publishing to GitHub publishes to CurseForge too. Runnable by hand for a re-upload.
#
# CurseForge wants numeric game-version IDs rather than names, and those IDs change as new versions
# are added, so they are looked up from the API every run instead of being hardcoded here.
#
# API reference: https://support.curseforge.com/en/support/solutions/articles/9000197321
set -euo pipefail

BASE="https://minecraft.curseforge.com"

JAR="${1:?usage: curseforge-upload.sh <jar> <minecraft-version> <changelog-file> [release-type]}"
MC_VERSION="${2:?missing minecraft version, e.g. 26.2}"
CHANGELOG_FILE="${3:?missing changelog file}"
RELEASE_TYPE="${4:-release}"

: "${CURSEFORGE_TOKEN:?set CURSEFORGE_TOKEN (create one at https://legacy.curseforge.com/account/api-tokens)}"
: "${CURSEFORGE_PROJECT_ID:?set CURSEFORGE_PROJECT_ID (shown on the CurseForge project page)}"

[ -f "$JAR" ] || { echo "!! No such jar: $JAR" >&2; exit 1; }
[ -f "$CHANGELOG_FILE" ] || { echo "!! No such changelog: $CHANGELOG_FILE" >&2; exit 1; }

api() { curl -sS --max-time 120 -H "X-Api-Token: $CURSEFORGE_TOKEN" "$@"; }

echo ">> Resolving CurseForge version IDs for Minecraft $MC_VERSION"
VERSIONS_JSON="$(api "$BASE/api/game/versions")"
if ! jq -e 'type == "array"' >/dev/null 2>&1 <<<"$VERSIONS_JSON"; then
    echo "!! Unexpected response from $BASE/api/game/versions — is the token valid?" >&2
    head -c 400 <<<"$VERSIONS_JSON" >&2; echo >&2
    exit 1
fi

# The Minecraft version itself. Exact name match: "26.2" must not match "26.2.1".
MC_ID="$(jq -r --arg v "$MC_VERSION" 'map(select(.name == $v)) | .[0].id // empty' <<<"$VERSIONS_JSON")"
if [ -z "$MC_ID" ]; then
    echo "!! CurseForge does not list Minecraft '$MC_VERSION' yet." >&2
    echo "!! Closest names it does know:" >&2
    jq -r --arg v "${MC_VERSION%%.*}" 'map(select(.name | startswith($v))) | .[].name' <<<"$VERSIONS_JSON" \
        | sort -u | tail -12 | sed 's/^/     /' >&2
    exit 1
fi

# The modloader tag, so the file is filtered correctly on the site.
LOADER_ID="$(jq -r 'map(select(.name == "NeoForge")) | .[0].id // empty' <<<"$VERSIONS_JSON")"
[ -n "$LOADER_ID" ] || echo "!! Warning: no 'NeoForge' modloader tag found; uploading without it." >&2

# CurseForge rejects an upload that names no environment ("You must select at least one version from
# the environment group of versions"). CityWorld needs both sides: the server generates the world,
# and the client supplies the world-type and Customize screens.
CLIENT_ID="$(jq -r 'map(select(.name == "Client")) | .[0].id // empty' <<<"$VERSIONS_JSON")"
SERVER_ID="$(jq -r 'map(select(.name == "Server")) | .[0].id // empty' <<<"$VERSIONS_JSON")"
if [ -z "$CLIENT_ID" ] || [ -z "$SERVER_ID" ]; then
    echo "!! Could not find the Client/Server environment tags CurseForge requires." >&2
    exit 1
fi

# The Java tag, so the file's requirements are visible on the site without reading the description.
# Minecraft 26.x ships the Java 25 runtime; the 1.21 line ships Java 21.
case "$MC_VERSION" in
    1.*) JAVA_VERSION=21 ;;
    *)   JAVA_VERSION=25 ;;
esac
JAVA_ID="$(jq -r --arg j "Java $JAVA_VERSION" 'map(select(.name == $j)) | .[0].id // empty' <<<"$VERSIONS_JSON")"
# Optional, unlike the environment: if CurseForge has not added a Java version yet (likely right
# after a new one ships) that must not block the upload, so warn and carry on without it.
[ -n "$JAVA_ID" ] || echo "!! Warning: CurseForge does not list 'Java $JAVA_VERSION'; uploading without a Java tag." >&2

GAME_VERSIONS="[$MC_ID${LOADER_ID:+,$LOADER_ID},$CLIENT_ID,$SERVER_ID${JAVA_ID:+,$JAVA_ID}]"
echo "   Minecraft $MC_VERSION = $MC_ID${LOADER_ID:+, NeoForge = $LOADER_ID}, Client = $CLIENT_ID, Server = $SERVER_ID${JAVA_ID:+, Java $JAVA_VERSION = $JAVA_ID}"

# "CityWorld 5.1.0 / MC 26.2" reads far better in the file list than the raw filename does. The mod
# version comes out of the filename (cityworld-5.1.0+mc26.2.jar), falling back to the whole basename
# if a jar is ever named differently. The file itself keeps its original name either way.
MOD_VERSION="$(basename "$JAR" .jar | sed -n 's/^cityworld-\(.*\)+mc.*$/\1/p')"
if [ -n "$MOD_VERSION" ]; then
    DISPLAY_NAME="CityWorld $MOD_VERSION / MC $MC_VERSION"
else
    DISPLAY_NAME="$(basename "$JAR" .jar)"
fi

METADATA="$(jq -n \
    --rawfile changelog "$CHANGELOG_FILE" \
    --arg displayName "$DISPLAY_NAME" \
    --arg releaseType "$RELEASE_TYPE" \
    --argjson gameVersions "$GAME_VERSIONS" \
    '{changelog: $changelog, changelogType: "markdown", displayName: $displayName,
      releaseType: $releaseType, gameVersions: $gameVersions}')"

if [ -n "${CURSEFORGE_DEBUG:-}" ]; then
    # The metadata carries no credentials, so it is safe to print when diagnosing a rejection.
    echo ">> metadata:"
    jq . <<<"$METADATA" | sed 's/^/     /'
fi

echo ">> Uploading $(basename "$JAR") to project $CURSEFORGE_PROJECT_ID ($RELEASE_TYPE)"
# --form-string, not -F: curl gives ';', a leading '@' and a leading '<' special meaning inside an
# -F value, and a changelog containing any of them silently mangles the JSON. CurseForge then
# answers "Error in field `metadata`: Invalid JSON", which reads like a bug in the JSON we built.
# --form-string sends the value literally. The jar still needs -F, since @ there is the point.
RESPONSE="$(curl -sS --max-time 600 -w '\n%{http_code}' \
    -H "X-Api-Token: $CURSEFORGE_TOKEN" \
    --form-string "metadata=$METADATA" \
    -F "file=@$JAR" \
    "$BASE/api/projects/$CURSEFORGE_PROJECT_ID/upload-file")"

STATUS="$(tail -n1 <<<"$RESPONSE")"
BODY="$(sed '$d' <<<"$RESPONSE")"

if [ "$STATUS" = "200" ]; then
    FILE_ID="$(jq -r '.id // empty' <<<"$BODY" 2>/dev/null || true)"
    echo ">> Uploaded${FILE_ID:+ as file $FILE_ID}"
    exit 0
fi

echo "!! CurseForge rejected the upload (HTTP $STATUS)" >&2
echo "$BODY" >&2
exit 1
