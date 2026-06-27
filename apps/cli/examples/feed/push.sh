#!/usr/bin/env bash
# Push the sample "Now" feed cards. Run `dayfold login` first. For the deep-link to
# resolve, push the example hub first: `bash ../hub-college/push.sh`.
set -euo pipefail
cd "$(dirname "$0")"
dayfold push sample-rsvp        c1-rsvp.json   --type invite
dayfold push sample-finaid-card c2-finaid.json --type contact
echo "Pushed. Open the app → Now (tap the Financial Aid card → it opens the college hub)."
