#!/bin/bash
# SessionStart hook: verify docs structure health.
# Checks: CLAUDE.md existence, doc index integrity, spec symlink, docs freshness.
# Always exits 0 — never blocks session start.

PROJECT_DIR="${1:-.}"
CLAUDE_MD="$PROJECT_DIR/CLAUDE.md"
DOCS_DIR="$PROJECT_DIR/docs/conventions"

if [ ! -f "$CLAUDE_MD" ]; then
  echo "[session-check] CLAUDE.md not found — consider creating one."
  exit 0
fi

# --- Doc index integrity: verify files referenced in CLAUDE.md exist ---
MISSING=0
check_doc() {
  local path="$PROJECT_DIR/$1"
  if [ ! -f "$path" ] && [ ! -d "$path" ]; then
    echo "[session-check] MISSING: $1 (referenced in CLAUDE.md but not found)"
    MISSING=1
  fi
}

# Extract doc paths from CLAUDE.md's markdown table (format: `path` | description)
while IFS= read -r line; do
  path=$(echo "$line" | sed -n 's/.*`\([^`]\+\)`.*/\1/p')
  if [ -n "$path" ] && [ "$path" != "docs" ]; then
    check_doc "$path"
  fi
done < <(grep -E '^\| `docs/' "$CLAUDE_MD" 2>/dev/null)

# --- Spec symlink check ---
SPEC_LINK="$PROJECT_DIR/docs/spec/current"
if [ -d "$SPEC_LINK" ]; then
  if [ -L "$SPEC_LINK" ]; then
    TARGET=$(readlink "$SPEC_LINK" 2>/dev/null)
    echo "[session-check] docs/spec/current -> $TARGET"
  elif [ -f "$SPEC_LINK/Junction" ] 2>/dev/null; then
    # Windows junction marker
    echo "[session-check] docs/spec/current is a Windows junction"
  else
    # Plain directory (not a symlink) — ok, just report
    echo "[session-check] docs/spec/current is a directory (not a symlink)"
  fi
else
  echo "[session-check] docs/spec/current not found — run scripts/create-spec-symlink.sh if needed"
fi

# --- Freshness check: warn if any core doc hasn't been updated in 14+ days ---
NOW=$(date +%s 2>/dev/null) || NOW=0
if [ "$NOW" -gt 0 ]; then
  STALE=""
  for doc in "$CLAUDE_MD" "$DOCS_DIR/backend/"{java-style,layering,database,filter-safety,step-advancement}.md "$DOCS_DIR/frontend/"{shared,admin,web}.md "$DOCS_DIR/api-rules.md" "$PROJECT_DIR/docs/architecture.md" "$PROJECT_DIR/docs/current-system-roadmap.md"; do
    LAST=$(git log -1 --format="%at" -- "$doc" 2>/dev/null)
    if [ -n "$LAST" ]; then
      DAYS=$(( (NOW - LAST) / 86400 ))
      if [ "$DAYS" -gt 14 ]; then
        STALE="$STALE  $(basename "$doc") ($DAYS days)\n"
      fi
    fi
  done
  if [ -n "$STALE" ]; then
    echo "[session-check] Docs stale (>14 days since last git update):"
    printf "$STALE"
  fi
fi

exit 0
