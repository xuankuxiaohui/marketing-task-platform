#!/usr/bin/env bash
# design §7.10: @Disabled / assumeTrue / skip tags forbidden in *IT / *PropertyTest / *ArchTest
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
hits="$(grep -R -n -E '@Disabled|assumeTrue' \
  --include='*IT.java' \
  --include='*PropertyTest.java' \
  --include='*ArchTest.java' \
  "$root/server" || true)"
if [ -n "$hits" ]; then
  echo "$hits" >&2
  echo "§7.10: @Disabled / assumeTrue forbidden in *IT / *PropertyTest / *ArchTest" >&2
  exit 1
fi
tag_hits="$(grep -R -n -E '@Tag[[:space:]]*\([[:space:]]*"(skip|wip|disabled|ignore)"' \
  --include='*IT.java' \
  --include='*PropertyTest.java' \
  --include='*ArchTest.java' \
  "$root/server" || true)"
if [ -n "$tag_hits" ]; then
  echo "$tag_hits" >&2
  echo "§7.10: skip @Tag forbidden in *IT / *PropertyTest / *ArchTest" >&2
  exit 1
fi
