#!/usr/bin/env bash
set -euo pipefail

EXPECTED_CODE="NG-REVERT-82E6F03"
CODE="${1:-}"

if [ "$CODE" != "$EXPECTED_CODE" ]; then
  echo "Pass the exact reverse code to restore NoteGraph UI to pre-ui-remake (82e6f03):"
  echo "  ./scripts/revert-ui-remake.sh $EXPECTED_CODE"
  exit 1
fi

if ! git rev-parse --verify pre-ui-remake >/dev/null 2>&1; then
  git fetch origin tag pre-ui-remake
fi

AHEAD=$(git rev-list pre-ui-remake..HEAD --count)
if [ "$AHEAD" -eq 0 ]; then
  echo "Already at pre-ui-remake baseline (82e6f03)."
  exit 0
fi

git revert --no-commit pre-ui-remake..HEAD
git commit -m "revert: restore UI prior to remake ($EXPECTED_CODE)"
echo "Reverted cleanly! Run 'git push origin main' to publish the restored UI."
