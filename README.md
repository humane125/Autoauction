# Autoauction

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## API Connection

The mod uses `config/autoauction.json`.

Set:

```json
{
  "apiBaseUrl": "https://lazy-similarly-reaffirm.ngrok-free.dev",
  "apiToken": "hpx_live_your_mod_key"
}
```

The API key must include `mod:connect` for the WebSocket heartbeat connection and `auction:read` for pricing requests.

When Minecraft starts and the player is available, the mod connects to:

```text
wss://<api-host>/api/mod/ws
```

The first message authenticates with the API key and current Minecraft username. After the server confirms auth, the mod sends `{ "type": "active" }`, sends `{ "type": "heartbeat" }` every 30 seconds, reports Hypixel joins and ban screens, and sends `{ "type": "offline" }` before the client socket closes. Timed Hypixel bans include reason, ban ID, and expiry metadata so the API can keep the account banned until the timer ends.

WebSocket connection, auth, inbound messages, active/offline status, heartbeat, close, and error events are logged to Minecraft's `logs/latest.log`. The raw API key is not written to logs.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
