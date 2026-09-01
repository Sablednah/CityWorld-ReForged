#!/usr/bin/env bash
# Check the Modrinth draft is ready, and point you at the website to submit it.
#
#   MODRINTH_TOKEN=xxx ./scripts/modrinth-submit.sh
#
# SUBMIT ON THE WEBSITE, NOT THROUGH THIS SCRIPT. Modrinth's submission form asks for an
# **AI-use declaration**, and v2 does not expose it - there is no such field anywhere in the
# published spec. A PATCH to `requested_status` therefore submits a project that has answered
# nothing, on a platform whose no-generative-AI review had already rejected our shield logo. Not
# worth it to save one click.
#
# So this prints what is about to be submitted and stops. The API path is still here behind
# MODRINTH_ALLOW_API_SUBMIT=1, for a RE-submission on a project whose declaration already exists.
#
# Modrinth will not accept a project with no versions, so upload at least one jar first.
set -euo pipefail

API="https://api.modrinth.com/v2"
SLUG="${MODRINTH_SLUG:-cityworld-reforged}"
: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN (a PAT with the PROJECT_WRITE scope)}"

UA="Sablednah/CityWorld-ReForged (sablecraft.co.uk)"
AUTH="Authorization: $MODRINTH_TOKEN"

INFO="$(curl -sS --max-time 60 -H "$AUTH" -H "User-Agent: $UA" "$API/project/$SLUG")"
python3 -c '
import json, sys
p = json.load(sys.stdin)
print(">> %s (%s)" % (p.get("title"), p.get("slug")))
print("   id:       %s" % p.get("id"))
print("   status:   %s" % p.get("status"))
print("   versions: %d" % len(p.get("versions") or []))
print("   gallery:  %d" % len(p.get("gallery") or []))
print("   icon:     %s" % ("set" if p.get("icon_url") else "MISSING"))
if not p.get("versions"):
    print("!! No versions uploaded. Modrinth will not accept a project with no files.", file=sys.stderr)
    sys.exit(1)
' <<<"$INFO"

if [ -z "${MODRINTH_ALLOW_API_SUBMIT:-}" ]; then
    cat <<MSG

>> Ready. Submit it on the website, not from here:
   https://modrinth.com/mod/$SLUG/settings

   The submission form asks for an AI-use declaration that the v2 API does not expose, so
   submitting through the API answers it with nothing. Given Modrinth's no-generative-AI review is
   what rejected our shield logo, that is not a form to skip.

   (Re-submitting a project whose declaration already exists? MODRINTH_ALLOW_API_SUBMIT=1.)
MSG
    exit 0
fi

echo ">> MODRINTH_ALLOW_API_SUBMIT set - submitting through the API"
STATUS="$(curl -sS --max-time 120 -o /tmp/modrinth-submit.out -w '%{http_code}' \
    -X PATCH -H "$AUTH" -H "User-Agent: $UA" -H "Content-Type: application/json" \
    --data '{"requested_status":"approved"}' "$API/project/$SLUG")"

case "$STATUS" in
    2*) echo ">> Submitted. Moderation runs now; the page stays private until it passes."
        echo "   https://modrinth.com/mod/$SLUG" ;;
    *)  echo "!! Submit failed (HTTP $STATUS)" >&2; cat /tmp/modrinth-submit.out >&2; exit 1 ;;
esac
