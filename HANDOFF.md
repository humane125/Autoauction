# AutoAuction Handoff

Date: 2026-06-17
Branch: `main`
Latest commit at handoff creation: `a7f539d`

## Current Setup

- Local repo: `C:\Projects\Hypixel\Minecraft Mod\26.1.1`
- GitHub remote: `https://github.com/humane125/Autoauction.git`
- Public API URL: `https://lazy-similarly-reaffirm.ngrok-free.dev`
- Built jar: `build\libs\autoauction-1.0.0.jar`
- Prism test mods folder: `C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods`

Do not commit real API tokens, Discord webhooks, Discord user IDs, local configs, or `logs/`.

## Current Behavior

- AutoAuction owns auction listing and macro handoff orchestration.
- Alt Manager owns account switching and proxy fetch.
- API websocket status uses `active`, `hypixel`, `offline`, and `banned`.
- Start/stop macro commands are client-side mod commands.
- Test listing mode lists each armor piece for `2,500,000`.
- Real listing mode asks the API for recommended BIN pricing.
- Final Destination recommendation requests use a fixed kill window of `25,000` to `30,000` kills.
- Click delay is hard-capped at a 400 ms minimum.
- Before taking off armor, the mod sends `/is` and waits 5.5 seconds.
- If inventory has no empty slot, the mod opens `/bz`, clicks `Sell Inventory Now`, confirms selling the whole inventory, then retries inventory-space checks.
- After listing armor, AutoAuction asks Alt Manager to switch to the next account, waits for proxy readiness, waits 1.5 seconds, reconnects to Hypixel, waits for player/server availability, then can send the configured macro start command.
- It waits 0.5 seconds after `client.player != null` and current server is available before macro start.

## Recent Changes

- Added handoff flow tests.
- Added `/is` and Bazaar sell-inventory handling before armor removal.
- Added fixed test-listing price and fixed Final Destination kill range.
- Added proxy-ready waiting and reconnect timing.
- Added inventory-space checks that read inventory state without opening the inventory screen.
- Removed stale `handoff2.md`; this file is now the canonical handoff.

## Known Open Issue

If the API Node server or ngrok restarts, the existing mod websocket can disconnect and not reconnect. Sending `active` again does nothing when the underlying websocket is closed. Next implementation should add reconnect/backoff in AutoAuction's websocket client:

- detect websocket close/error
- mark socket disconnected
- schedule reconnect with backoff
- after `auth_ok`, resend current status (`active` or `hypixel`)
- restart heartbeat only after `auth_ok`

## Config

Each Prism instance uses:

```text
minecraft\config\autoauction.json
```

Important fields:

```json
{
  "apiBaseUrl": "https://lazy-similarly-reaffirm.ngrok-free.dev",
  "apiToken": "hpx_live_your_mod_key",
  "macroStopCommand": "your client-side stop command",
  "macroStartCommand": "your client-side start command"
}
```

The API key should have:

- `mod:connect` for websocket status
- `auction:read` for pricing

## Verification

Run locally:

```powershell
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

Last local verification before this handoff:

- `.\gradlew.bat --no-daemon test` passed.

## Build And Copy

```powershell
.\gradlew.bat --no-daemon build
Copy-Item "C:\Projects\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods\autoauction-1.0.0.jar" -Force
```

## Next Work

1. Implement websocket reconnect/backoff after API or ngrok restarts.
2. After reconnect, resend current status so dashboard does not stay offline.
3. Add dashboard visibility for connected mod clients and last heartbeat.
4. Decide how to surface API `account_deleted` errors in the mod.
