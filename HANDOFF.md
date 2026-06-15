# Handoff Notes

## What Changed

- Added `ModSocketClient` using Java's built-in `HttpClient.newWebSocket`.
- The mod now reuses existing `apiBaseUrl` and `apiToken`.
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

- After `auth_ok`, the mod sends heartbeat messages.
- Added unit tests for socket URI conversion, auth payload, heartbeat behavior, and no-token behavior.

## Config Needed

Edit `config/autoauction.json`:

```json
{
  "apiBaseUrl": "https://lazy-similarly-reaffirm.ngrok-free.dev",
  "apiToken": "hpx_live_your_mod_key"
}
```

The API key should have:

- `mod:connect` for WebSocket heartbeat/registration
- `auction:read` for pricing requests

## Verification Used

The mod project requires JDK 25. On this machine the installed path is:

```text
C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot
```

Verification command:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --no-daemon test
```

The full mod test suite passed before this handoff.

## Next Work

1. Add ban detection socket message from the mod.
2. Add server broadcast handling for `disconnect_now`.
3. Wire mod receive-side commands so the server can ask clients to disconnect safely.
4. Add reconnect/backoff behavior for the socket client if the API server or ngrok tunnel restarts.
