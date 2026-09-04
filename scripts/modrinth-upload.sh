#!/usr/bin/env bash
# Upload one built jar to the Modrinth project as a version.
#
#   MODRINTH_TOKEN=xxx ./scripts/modrinth-upload.sh <jar> <changelog-file> [minecraft-version] [release-type]
#
# Normally run for you by .github/workflows/modrinth.yml when a GitHub release is published, so
# publishing to GitHub publishes to Modrinth as well as CurseForge. Runnable by hand for a re-upload.
#
# CityWorld ships a jar per Minecraft version, and each becomes its OWN Modrinth version rather than
# three files on one. Modrinth version numbers must be unique within a project, so the version
# number carries the +mc suffix the jar already has: 3.4.0+mc1.21.11, 3.4.0+mc26.2, and so on. That
# also means the Minecraft version can be read off the filename, exactly as the CurseForge upload
# does - this workflow only ever checks out the tag's ref, so gradle.properties there describes one
# of the three and would mislabel the other two.
#
# API reference: https://docs.modrinth.com/api/  (spec: https://docs.modrinth.com/openapi.yaml)
set -euo pipefail

API="https://api.modrinth.com/v2"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SLUG="${MODRINTH_SLUG:-cityworld-reforged}"

JAR="${1:?usage: modrinth-upload.sh <jar> <changelog-file> [minecraft-version] [release-type]}"
CHANGELOG_FILE="${2:?missing changelog file}"
MC_VERSION="${3:-}"
RELEASE_TYPE="${4:-release}"

: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN (a PAT with the VERSION_CREATE scope)}"

UA="Sablednah/CityWorld-ReForged (sablecraft.co.uk)"
AUTH="Authorization: $MODRINTH_TOKEN"

[ -f "$JAR" ]            || { echo "!! No such jar: $JAR" >&2; exit 1; }
[ -f "$CHANGELOG_FILE" ] || { echo "!! No such changelog: $CHANGELOG_FILE" >&2; exit 1; }

BASENAME="$(basename "$JAR" .jar)"
# cityworld-5.4.0+mc26.2 -> 5.4.0+mc26.2
VERSION_NUMBER="$(sed -n 's/^cityworld-\(.*\)$/\1/p' <<<"$BASENAME")"
VERSION_NUMBER="${VERSION_NUMBER:-$BASENAME}"

if [ -z "$MC_VERSION" ]; then
    MC_VERSION="$(sed -n 's/.*+mc\([0-9][0-9.]*\)$/\1/p' <<<"$BASENAME")"
    if [ -z "$MC_VERSION" ]; then
        # No suffix: a single-version release from before the branches existed.
        MC_VERSION="$(sed -n 's/^minecraft_version=\(.*\)$/\1/p' "$HERE/gradle.properties")"
        echo "   no +mc suffix; falling back to gradle.properties -> $MC_VERSION"
    fi
fi
[ -n "$MC_VERSION" ] || { echo "!! Could not determine a Minecraft version." >&2; exit 1; }

# Modrinth rejects an unknown game version with a validation error that names the field but not the
# value, so check it here where the message can be useful. This is the expected failure right after
# a Minecraft release, before Modrinth has added the version.
echo ">> Checking Modrinth knows Minecraft $MC_VERSION"
curl -sS --max-time 60 -H "User-Agent: $UA" "$API/tag/game_version" \
  | MC="$MC_VERSION" python3 -c '
import json, os, sys
mc = os.environ["MC"]
versions = json.load(sys.stdin)
if any(v.get("version") == mc for v in versions):
    sys.exit(0)
rel = [v["version"] for v in versions if v.get("version_type") == "release"][:12]
print("!! Modrinth does not list Minecraft %r yet. Newest releases it knows: %s"
      % (mc, ", ".join(rel)), file=sys.stderr)
sys.exit(1)'

# `project_id` must be the project's base62 ID, NOT its slug. Passing the slug gives
#   400 invalid_input "Error while parsing JSON: Invalid character '-' in base62 encoding"
# because "cityworld-reforged" contains a hyphen - a message that names neither the field nor the
# slug, and points at a column deep inside the embedded changelog. Every other endpoint here takes
# `{id|slug}` interchangeably; this body field does not.
echo ">> Resolving the project ID for /$SLUG"
LOOKUP="$(curl -sS --max-time 60 -w '\n%{http_code}' -H "$AUTH" -H "User-Agent: $UA" "$API/project/$SLUG")"
LOOKUP_STATUS="$(tail -n1 <<<"$LOOKUP")"
if [ "$LOOKUP_STATUS" != "200" ]; then
    echo "!! Could not read the project at /$SLUG (HTTP $LOOKUP_STATUS)." >&2
    [ "$LOOKUP_STATUS" = "404" ] && echo "!! A draft is invisible without authentication, so a 404 here means either the token is not authorised for it or scripts/modrinth-create.sh has not been run." >&2
    exit 1
fi
PROJECT_ID="$(sed '$d' <<<"$LOOKUP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
[ -n "$PROJECT_ID" ] || { echo "!! The project lookup returned no id." >&2; exit 1; }
echo "   $SLUG = $PROJECT_ID"

# `environment` is a version field on v2 (the project-level pair is client_side/server_side).
# server_only_client_optional is the exact truth: the mod does its work on the server, and a client
# that has it also gets the Customize screen when creating a single-player world.
DATA="$(CHANGELOG="$CHANGELOG_FILE" PID="$PROJECT_ID" VN="$VERSION_NUMBER" MC="$MC_VERSION" \
        RTYPE="$RELEASE_TYPE" NAME="CityWorld $VERSION_NUMBER" python3 -c '
import json, os
print(json.dumps({
  "name": os.environ["NAME"],
  "version_number": os.environ["VN"],
  "changelog": open(os.environ["CHANGELOG"], encoding="utf-8").read(),
  "dependencies": [],
  "game_versions": [os.environ["MC"]],
  "version_type": os.environ["RTYPE"],
  "loaders": ["neoforge"],
  "featured": True,
  "environment": "server_only_client_optional",
  "project_id": os.environ["PID"],
  "file_parts": ["file"],
  "primary_file": "file",
}))')"

if [ -n "${MODRINTH_DEBUG:-}" ]; then
    # The metadata carries no credentials, so it is safe to print when diagnosing a rejection.
    echo ">> metadata:"; python3 -m json.tool <<<"$DATA" | sed 's/^/     /'
fi

echo ">> Uploading $(basename "$JAR") as version $VERSION_NUMBER (Minecraft $MC_VERSION, $RELEASE_TYPE)"
# --form-string for the metadata, -F for the jar. curl gives ';', a leading '@' and a leading '<'
# special meaning inside an -F value, and a changelog containing any of them silently mangles the
# JSON; the jar still needs -F, since @ there is the point.
RESPONSE="$(curl -sS --max-time 600 -w '\n%{http_code}' \
    -H "$AUTH" -H "User-Agent: $UA" \
    --form-string "data=$DATA" \
    -F "file=@$JAR" \
    "$API/version")"

STATUS="$(tail -n1 <<<"$RESPONSE")"
BODY="$(sed '$d' <<<"$RESPONSE")"

case "$STATUS" in
    2*) VERSION_ID="$(python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("id",""))
except Exception: pass' <<<"$BODY" 2>/dev/null || true)"
        echo ">> Uploaded${VERSION_ID:+ as version $VERSION_ID}"
        echo "   https://modrinth.com/mod/$SLUG/version/$VERSION_NUMBER"
        exit 0 ;;
esac

echo "!! Modrinth rejected the upload (HTTP $STATUS)" >&2
echo "$BODY" >&2
case "$STATUS" in
    401) echo "!! 401 is the token. Modrinth takes the raw token with NO 'Bearer ' prefix, and the PAT needs the VERSION_CREATE scope." >&2 ;;
    404) echo "!! 404 means no project at /$SLUG. Run scripts/modrinth-create.sh first - a version cannot be uploaded to a project that does not exist." >&2 ;;
    400) if grep -qi duplicate <<<"$BODY"; then
             echo "!! This version number is already up. Modrinth version numbers are unique per project, which is why the +mc suffix is part of ours." >&2
         else
             echo "!! A 400 here is a rejected field. Note the column it names counts into the JSON, most of which is the embedded changelog, so it rarely points at the offending field. Re-run with MODRINTH_DEBUG=1 to print the metadata." >&2
         fi ;;
esac
exit 1
