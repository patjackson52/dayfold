#!/usr/bin/env bash
# Push the sample "Starting College" hub to your family. Run `dayfold login` first.
# Then open the app → Hubs → "Sample → Starting College" to see every block type render.
set -euo pipefail
cd "$(dirname "$0")"
dayfold push sample-college hub.json       --hub
dayfold push sample-dates   s1-dates.json  --section
dayfold push sample-money   s2-money.json  --section
dayfold push sample-ebill   b1-ebill.json  --block
dayfold push sample-forms   b2-forms.json  --block
dayfold push sample-finaid  b3-finaid.json --block
dayfold push sample-imm     b4-imm.json    --block
dayfold push sample-budget  b5-budget.json --block
dayfold push sample-notes   b6-notes.json  --block
dayfold push sample-campus  b7-campus.json --block
echo "Pushed. Open the app → Hubs → 'Sample → Starting College'."
