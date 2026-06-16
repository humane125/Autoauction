# Handoff Notes

## Current Setup

- Mod repo: `C:\Humane\Hypixel\Minecraft Mod\26.1.1`
- Public API URL: `https://lazy-similarly-reaffirm.ngrok-free.dev`
- Built jar: `build\libs\autoauction-1.0.0.jar`
- Prism instances currently used for testing:
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1\minecraft`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1(1)\minecraft`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1(2)\minecraft`

Do not commit real API tokens, Discord webhooks, Discord user IDs, local config files, or `logs/`.

## What Changed

- Added `ModSocketClient` using Java's built-in `HttpClient.newWebSocket`.
- The mod reuses existing `apiBaseUrl` and `apiToken`.
- `https://` API URLs are converted to `wss://.../api/mod/ws`; `http://` is converted to `ws://.../api/mod/ws`.
- Once Minecraft has a player, the mod sends:

```json
{
  "type": "auth",
  "apiKey": "...",
  "username": "MinecraftName",
  "clientVersion": "1.0.0"
}
```

- After `auth_ok`, the mod sends `{ "type": "active" }`, then heartbeats every 30 seconds.
- The mod reports Hypixel joins as `{ "type": "hypixel" }`.
- The mod reports ban screens as `{ "type": "banned", "banReason": "...", "banId": "...", "banUntil": "..." }`.
- When a ban is detected and reported successfully, the mod sends a Discord alert through the existing `DiscordNotifier`.
- Discord ban alerts use the configured Discord user ID mention and include the Minecraft username and reason.
- The mod now receives server messages. When it receives `{ "type": "disconnect_now", "reason": "..." }`, it disconnects Minecraft on the main client thread.
- When the Minecraft client is stopping or the socket client closes, the mod sends `{ "type": "offline" }` before closing the WebSocket. The API preserves active timed bans through that offline message.
- Added unit tests for socket URI conversion, auth payload, active/offline/hypixel/banned status messages, heartbeat behavior, ban parsing, no-token behavior, remote disconnect commands, and Discord ban alerts.

## Config Needed

Each Prism instance has `minecraft\config\autoauction.json`. It should contain these fields:

```json
{
  "apiBaseUrl": "https://lazy-similarly-reaffirm.ngrok-free.dev",
  "apiToken": "hpx_live_your_mod_key",
  "discordWebhookUrl": "https://discord.com/api/webhooks/...",
  "discordUserId": "123456789"
}
```

The API key should have:

- `mod:connect` for WebSocket heartbeat/registration
- `auction:read` for pricing requests

Multiple Minecraft instances can share one API key if all of those accounts should belong to the same dashboard user.

## Build And Copy

Run from the mod repo:

```powershell
.\gradlew.bat --no-daemon build
```

Copy the jar into the Prism instances:

```powershell
Copy-Item "C:\Humane\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "C:\Humane\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1(1)\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "C:\Humane\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.1(2)\minecraft\mods\autoauction-1.0.0.jar" -Force
```

## Verification Used

```powershell
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

Both passed before this handoff.

## Next Work

1. Add reconnect/backoff behavior if the API server or ngrok tunnel restarts.
2. Decide how the mod should display or recover from the API `account_deleted` socket error.
3. Add a small dev/test command to simulate a ban without waiting for a real ban screen.
4. Add richer dashboard visibility for current socket clients and last heartbeat.
