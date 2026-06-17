# AutoAuction Handoff 2

Date: 2026-06-17
Branch: `26.1.2`

## What changed

- Fixed Final Destination armor kill parsing:
  - strips legacy formatting codes
  - reads `Kills: 25,437`
  - reads Hypixel upgrade progress like `Next Upgrade: +395 (24,393/25,000)`
- Added `/autoauction armor` to print equipped Final Destination armor kills and readiness.
- Changed the mod socket so account changes reconnect the websocket under the new username.
- Simplified config:
  - removed `dryRun`
  - removed private-server-only guard
  - removed `allowedServers`
  - removed min/max BIN price filters because API recommendations own pricing
  - `enabledByDefault` now defaults to `true`
- Added `macroStartCommand` as an optional config field.
- Added handoff to Alt Manager after real listing:
  - after all armor is listed, AutoAuction requests Alt Manager's next account
  - Alt Manager switches account and fetches that account proxy
  - AutoAuction waits for the username to change
  - AutoAuction reconnects to `mc.hypixel.net`
  - once on Hypixel, AutoAuction resumes watching armor
  - if `macroStartCommand` is configured, AutoAuction sends it after reconnect

## Current config note

`macroStartCommand` is currently blank in the local config, so macro restart is skipped until a start command is configured.
`macroStopCommand` is still required for the real listing flow.

## Tests and build

- Added parser tests for formatted kills and upgrade-progress kills.
- Added armor formatter/debug command tests.
- Added config migration tests.
- Added handoff bridge tests.
- Full AutoAuction `gradlew build` passed.
- Rebuilt `autoauction-1.0.0.jar` was copied to:
  - `C:\Humane\Hypixel\Minecraft Mod\Alt Manager\run\mods\autoauction-1.0.0.jar`

## Runtime notes

- `logs/` remains untracked in the AutoAuction repo.
- The handoff bridge uses reflection so AutoAuction does not need a compile-time dependency on Alt Manager.
