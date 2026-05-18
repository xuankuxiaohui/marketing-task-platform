#!/bin/bash
# SessionStart hook: remind if CLAUDE.md hasn't been updated in a while.
# Always exits 0 so it never blocks session start.

CLAUDE_MD="$1/CLAUDE.md"
if [ -z "$1" ]; then
  CLAUDE_MD="CLAUDE.md"
fi

if [ ! -f "$CLAUDE_MD" ]; then
  echo "[claude-md-check] CLAUDE.md not found — consider creating one."
  exit 0
fi

# Check when CLAUDE.md was last modified in git
LAST_COMMIT=$(git log -1 --format="%at" -- "$CLAUDE_MD" 2>/dev/null)
NOW=$(date +%s)

if [ -n "$LAST_COMMIT" ]; then
  DAYS=$(( (NOW - LAST_COMMIT) / 86400 ))
  if [ "$DAYS" -gt 14 ]; then
    echo "[claude-md-check] CLAUDE.md last updated $DAYS days ago. Consider refreshing '当前状态' section."
  fi
fi

# Check if "当前状态" section mentions current sprint
if grep -q "进行中" "$CLAUDE_MD" 2>/dev/null; then
  echo "[claude-md-check] CLAUDE.md says '进行中' — verify sprint status is still accurate."
fi

exit 0
