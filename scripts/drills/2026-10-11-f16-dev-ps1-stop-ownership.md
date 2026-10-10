# F16 `dev.ps1 stop` ownership closure — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master synced) | `67444ebb0efb49d1c6170989a8b49ff2c80f6028` (merge #127) |
| Branch | `feat/p4-f16-dev-ps1-stop-ownership` |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~01:03–01:20 CST |
| Host ownership | **Windows host-owned** — `scripts/dev.ps1` is native PowerShell (`#Requires -Version 5.1`, Win32_Process, `taskkill`, `netstat`) |
| Shared/prod DB touched | **No** |
| DEC-005/006 / business SQL | **Not touched** |

## Goal

Close F16 leftover for local stop ownership (#118 intent): stop via `.run` PID first; port kill only when owned; otherwise skip unless `-Force`. Document Windows ownership and mark **bot Linux box runtime as N/A** (no fabricated Windows evidence).

## Architect intent (from #118)

1. `stop` / `restart` terminate `.run/<name>.pid` process tree first.
2. If the port is still LISTENING, kill only when ownership is verified:
   - saved PID match
   - saved PID parent tree
   - cmdline fingerprint (jar / `--filter` + vite|pnpm)
3. Unowned listener → warn + skip; explicit `-Force` overrides.

## Hardening this round (gaps vs #118)

| Gap | Change |
|---|---|
| Cmdline fingerprint could match another checkout's jar/vite | `Test-PathBoundToRepo`: fingerprint path must contain this repo `$Root` |
| Node filter matched loose substring `admin`/`client` | Require `--filter <name>` token |
| Parent walk unbounded | Depth cap 32 |
| Port re-check immediately after kill | 400ms settle before `Get-ListeningPid` |
| Help lacked `-Force` example | Added `stop -Force` |

## Runtime evidence — **N/A on bot box**

| Check | Result |
|---|---|
| `pwsh` / Windows PowerShell available | **N/A** — not installed on bot Linux box |
| Start four local processes then `dev.ps1 stop` | **N/A** — requires Windows host + JDK 26 + local MySQL/Redis |
| Port foreign-occupancy skip / `-Force` live kill | **N/A** — do not invent fake Windows runtime logs |

Operators must run the Windows checklist below on a real Windows host when taking runtime evidence.

### Windows host checklist (for future runtime evidence)

```powershell
# From repo root on Windows (after init + middleware up):
.\scripts\dev.ps1 start
.\scripts\dev.ps1 status
.\scripts\dev.ps1 stop          # expect owned PIDs stopped; .run/*.pid removed
# Foreign port: hold 8080 with unrelated process, ensure .run/admin.pid absent/stale mismatched:
.\scripts\dev.ps1 stop -Target admin           # expect warn + skip
.\scripts\dev.ps1 stop -Target admin -Force    # expect warn + kill
```

## Static review (bot box — real)

```bash
bash scripts/drills/static-review-dev-ps1-stop.sh
```

Outcome on this executor (2026-10-11):

```
PASS  param -Force switch
PASS  Test-PathBoundToRepo helper
PASS  Test-OwnedByProject helper
PASS  ownership reads .run saved PID
PASS  ownership walks parent process tree
PASS  parent walk depth cap (32)
PASS  cmdline fingerprint path-bound to repo Root
PASS  admin jar cmdline fingerprint
PASS  portal jar cmdline fingerprint
PASS  node --filter token in ownership fingerprint
PASS  Stop-Service: .run PID kill before port lookup
PASS  settle sleep after .run PID kill
PASS  help documents stop -Force
PASS  help describes ownership-first stop

ALL STATIC CHECKS PASSED (Windows runtime N/A on this host)
```

## Digests (at evidence time)

| Path | SHA256 |
|---|---|
| `scripts/dev.ps1` | `6ca34f264adb9a18b22772314ee569190b3477191d278c16efa15d4ad1c6a7bf` |
| `scripts/drills/static-review-dev-ps1-stop.sh` | `5776549990fe1302da19c96b3a7314807f45197c3e922e807ed0ea4c819209e1` |

## Gaps (explicit)

- No Windows live start/stop/Force transcript in this PR (host-owned; bot box N/A).
- Does not change Compose / deploy stop paths.
- DEC-005/006 still parked.
