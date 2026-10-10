#!/usr/bin/env bash
# Static ownership review for scripts/dev.ps1 stop path (Linux-safe).
# Does NOT execute PowerShell / kill processes / touch Windows hosts.
# Exit 0 = all checklist items present; non-zero = gap.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PS1="$ROOT/scripts/dev.ps1"
fail=0
pass() { printf 'PASS  %s\n' "$1"; }
fail_item() { printf 'FAIL  %s\n' "$1"; fail=1; }

[[ -f "$PS1" ]] || { echo "missing $PS1"; exit 2; }

grep -qE '\[switch\]\$Force' "$PS1" && pass 'param -Force switch' || fail_item 'param -Force switch'

grep -q 'function Test-PathBoundToRepo' "$PS1" && pass 'Test-PathBoundToRepo helper' || fail_item 'Test-PathBoundToRepo helper'

grep -q 'function Test-OwnedByProject' "$PS1" && pass 'Test-OwnedByProject helper' || fail_item 'Test-OwnedByProject helper'

grep -q 'Read-SavedPid \$Name' "$PS1" && pass 'ownership reads .run saved PID' || fail_item 'ownership reads .run saved PID'

grep -q 'ParentProcessId' "$PS1" && pass 'ownership walks parent process tree' || fail_item 'ownership walks parent process tree'

grep -q 'depth -lt 32' "$PS1" && pass 'parent walk depth cap (32)' || fail_item 'parent walk depth cap (32)'

grep -q 'Test-PathBoundToRepo \$cmd' "$PS1" && pass 'cmdline fingerprint path-bound to repo Root' || fail_item 'cmdline fingerprint path-bound to repo Root'

grep -qF 'admin-app-.*\.jar' "$PS1" && pass 'admin jar cmdline fingerprint' || fail_item 'admin jar cmdline fingerprint'

grep -qF 'portal-app-.*-exec\.jar' "$PS1" && pass 'portal jar cmdline fingerprint' || fail_item 'portal jar cmdline fingerprint'

grep -q -- '--filter' "$PS1" && pass 'node --filter token in ownership fingerprint' || fail_item 'node --filter token in ownership fingerprint'

# Stop-Service order: Stop-PidTree on saved before Get-ListeningPid
python3 - <<'PY' "$PS1" && pass 'Stop-Service: .run PID kill before port lookup' || fail_item 'Stop-Service: .run PID kill before port lookup'
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text(encoding='utf-8')
start = text.index('function Stop-Service([string]$Name) {')
end = text.index('\nfunction Start-Detached', start)
body = text[start:end]
i_saved_kill = body.find('Stop-PidTree $saved')
i_port = body.find('Get-ListeningPid $svc.Port')
i_owned = body.find('Test-OwnedByProject $Name $portPid')
i_force = body.find('elseif ($Force)')
i_skip = body.find('跳过端口强杀')
ok = (
    i_saved_kill != -1 and i_port != -1 and i_owned != -1 and i_force != -1 and i_skip != -1
    and i_saved_kill < i_port < i_owned < i_force
    and i_owned < i_skip
)
raise SystemExit(0 if ok else 1)
PY

grep -q 'Start-Sleep -Milliseconds 400' "$PS1" && pass 'settle sleep after .run PID kill' || fail_item 'settle sleep after .run PID kill'

grep -q 'stop -Force' "$PS1" && pass 'help documents stop -Force' || fail_item 'help documents stop -Force'

grep -q '先 .run pid' "$PS1" && pass 'help describes ownership-first stop' || fail_item 'help describes ownership-first stop'

echo
if [[ "$fail" -eq 0 ]]; then
  echo "ALL STATIC CHECKS PASSED (Windows runtime N/A on this host)"
  exit 0
fi
echo "STATIC CHECKS FAILED ($fail)"
exit 1
