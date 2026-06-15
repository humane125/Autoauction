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

The first message authenticates with the API key and current Minecraft username. After the server confirms auth, the mod sends heartbeat messages so the dashboard can show the account as active or offline.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
