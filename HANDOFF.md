# AutoAuction Handoff

Date: 2026-06-30
Branch: `main`
Latest local commit before this handoff: `c86cac7 Send lightweight account wealth stats`

## Current Setup

- Local repo: `C:\Humane\Hypixel\Minecraft Mod\26.1.1`
- GitHub remote: `https://github.com/humane125/Autoauction.git`
- Current public API URL: `https://humane-hypixel.duckdns.org`
- Old ngrok URL is deprecated: `https://lazy-similarly-reaffirm.ngrok-free.dev`
- Built jar: `build\libs\autoauction-1.0.0.jar`
- Active Prism mod folders:
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test(1)\minecraft\mods`

Do not commit API tokens, Discord webhooks, local Prism configs, generated `logs/`, or Minecraft account data.

## Test Later

Test account wealth stat reporting with real Hypixel/Nebula instances after the API/dashboard slice is deployed:

- Confirm AutoAuction websocket authenticates and the dashboard account card starts showing wealth stats for that Minecraft account.
- Equip all four Final Destination armor pieces and confirm the remote Account Wealth panel shows per-piece kills and lowest-piece kills.
- Confirm purse updates at least every 30 seconds, and faster when values change.
- Drop a Summoning Eye and confirm only a tiny `summoning_eye_event` is sent, not full logs/screenshots.
- Create a Summoning Eye sell order and confirm the dashboard moves eyes from held to listed with the detected per-eye price.
- Instant-sell Summoning Eyes and confirm the dashboard removes the sold eye quantity from held/listed counts.
- Switch accounts through Alt Manager and confirm stats stay tied to the Minecraft UUID, not the Prism instance.

Test the new End lobby collision switch with real macro instances later. The code is pushed and the jar was copied locally, but this behavior still needs live Hypixel/Nebula verification because Nebula is HWID locked on this machine.

Test scenario:

- Start at least two registered accounts that can appear in The End.
- Make sure AutoAuction websocket connects to the deployed API.
- Confirm the mod receives the registered account list after socket auth.
- Let one macro account enter The End where another registered account is visible in tablist.
- Expected flow for the newly arriving account:
  - Detect `Area: The End`.
  - Detect another registered username in tablist, excluding itself.
  - Ensure Nebula combat macro is off.
  - Run `/is`.
  - Wait about 1 second after player is loaded.
  - Ensure Nebula combat macro is on again.
  - If Nebula returns to another occupied End lobby, repeat the switch.
- Confirm the older account already farming in the lobby does not also leave at the same time.

## Latest Changes

- Added lightweight account wealth reporting:
  - New `AccountStatsSnapshot` payload sends purse and Final Destination armor kills.
  - New `SummoningEyeEventDetector` parses compact chat events for:
    - Summoning Eye drops
    - Bazaar instant-sell completions
    - Bazaar sell-order setup with per-eye price
  - `ModSocketClient.sendAccountStats` sends `account_stats`.
  - `ModSocketClient.sendSummoningEyeEvent` sends `summoning_eye_event`.
  - AutoAuction polls stats every 20 ticks but only sends when values change or a 30 second heartbeat elapses.
  - Chat handling sends small structured eye events; it does not stream full logs for wealth stats.
- Recent local commits:
  - `ef5ae56 Cancel lobby collision on manual macro stop`
  - `c86cac7 Send lightweight account wealth stats`
- Verified locally:
  - `.\gradlew.bat --no-daemon test`
  - `.\gradlew.bat --no-daemon build`
- Built `build\libs\autoauction-1.0.0.jar` and copied it to all three active Prism mod folders on 2026-06-30.

- Added End lobby collision switching:
  - New `LobbyCollisionController` tracks registered usernames, current area, tablist names, macro state, and switch workflow state.
  - Collision detection only runs in `The End`.
  - The current Minecraft username is ignored when matching tablist names.
  - Registered account matching is case-insensitive and works from tablist text with rank prefixes.
  - Switch workflow sends Nebula toggle through the guarded client-command path, then `/is`, then toggles Nebula back on after player stability.
- Mod websocket now requests `registered_accounts` after `auth_ok`.
- Mod websocket parses incoming `registered_accounts` and passes the full registered username list into the collision controller.
- Added tests:
  - `ModSocketClientTest.requestsAndDispatchesRegisteredAccountsAfterAuthOk`
  - `LobbyCollisionControllerTest`
- Verified locally:
  - `.\gradlew.bat --no-daemon test`
  - `.\gradlew.bat --no-daemon build`
- Built jar was copied to:
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods`
  - `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test(1)\minecraft\mods`

## What Happened

- Transfer automation was completed beyond the old buy-order-only handoff.
- Sender now instant-sells into the receiver buy order, then instant-buys the receiver sell offer.
- Receiver now claims the filled buy order, creates the sell offer, waits for sender buy-back, claims coins, and sends `transfer_cycle_complete`.
- Transfer target loops now continue until the receiver-reported purse delta reaches or exceeds the requested target.
- Final target output now reports receiver starting purse, done purse, and total delta.
- Receiver claim delta waits for purse update to avoid stale scoreboard/negative delta.
- Sender transfer preparation now calculates a safe full-stack transfer quantity before each run.
- There is no automatic coin reserve subtraction; the operator should set the target lower if they want coins left over.
- Sender only lets the receiver create a buy order after sender preparation completes. If parking is needed, sender opens `/ec`, parks/rebalances first, closes EC, then sends the receiver run.
- If the current inventory plus already parked EC stacks are already balanced for the next run, sender skips `/ec` and continues normally.
- Parked stacks persist across target-loop cycles. The next cycle treats inventory plus tracked parked stacks as available quantity, then parks more or restores only the stacks needed for that cycle.
- Parked stacks restore when a single run completes, the target is reached, the loop stops, the transfer is cancelled, or an error path needs cleanup.
- The current EC parking slice only moves full stacks. It does not split partial stacks or select exact custom item counts from inventory.
- Sender instant-sell now left-clicks `Sell Instantly` once. EC parking is what makes the inventory safe, so no instant-sell amount screen is used.
- If the optional instant-sell warning appears, sender waits 6 seconds, confirms, and closes Bazaar.
- Sender instant-buy uses the two-step custom amount flow:
  - Click `Buy Instantly`.
  - Click the amount-screen `Custom Amount` slot 16.
  - Type the quantity on the sign.
  - On `Confirm Instant Buy`, click slot 13.
  - If the same `Confirm` warning screen appears, wait 6 seconds and click `WARNING` slot 13.
  - Wait for the Bazaar bought chat before notifying the receiver.
- Transfer debug messages were made clearer with workflow, state, item, quantity, and delay details.
- Bazaar estimate math was fixed: Hypixel `quick_status.sellPrice` is the receiver buy cost side and `quick_status.buyPrice` is the receiver sell revenue side.
- Bazaar product ID resolution was hardened:
  - Exact product ID match first.
  - Aliases for known mismatches such as `NETHER_WART_DISTILLATE -> NETHER_STALK_DISTILLATE`.
  - Ordered-token fallback for cases like `Glowstone Distillate -> GLOWSTONE_DUST_DISTILLATE`.
  - Ambiguous matches do not guess.
- Latest jars were copied to all three listed Prism mod folders after build.
- The 26.1.2 Prism runtime configs were manually updated to `https://humane-hypixel.duckdns.org`.
- API/dashboard remote-control shell is now deployed on the RDP:
  - Dashboard `Connect` opens `/remote/<minecraft_uuid>`.
  - Manual `/remote/<username>` and UUID-without-dashes routes also resolve.
  - Dashboard can request screenshots over `/api/dashboard/ws`.
  - API relays screenshot requests to the connected mod socket as `request_screenshot`.
  - API accepts `client_screenshot` and `client_log` from the mod.
  - Dashboard shows screenshot state and in-game/client logs on the remote-control page.
- The API repo latest deployed commit for this handoff is `86acad0 Use account keys for remote control routes`.

## Current Transfer Commands

- `/autoauction transfer list`
- `/autoauction transfer <receiverUsername> <itemName...>`
- `/autoauction transfer accept <senderUsername>`
- `/autoauction transfer decline <senderUsername>`
- `/autoauction transfer cancel`
- `/autoauction transfer run`
- `/autoauction transfer run <targetCoins>`

Target examples support suffixes such as `k`, `m`, and `b`.

## Current Runtime Behavior

- AutoAuction websocket connects to `<apiBaseUrl>/api/mod/ws`, converting `https://` to `wss://`.
- Current expected socket URL is `wss://humane-hypixel.duckdns.org/api/mod/ws`.
- AutoAuction websocket reconnects after unexpected close/error with backoff and resends the last desired account status after `auth_ok`.
- Start/stop macro commands are client-side mod commands.
- Before taking off armor, the mod sends `/is` and waits 5.5 seconds.
- If inventory has no empty slot, the mod opens `/bz`, clicks sell-inventory flow, confirms, then retries.
- After armor listing, AutoAuction asks Alt Manager for account switch/proxy handoff, waits for proxy readiness, waits 1.5 seconds, reconnects to Hypixel, waits for player/server availability, waits 0.5 seconds, then can run the macro start command.
- Bazaar transfer starts from private island checks where implemented.
- Bazaar transfer target safety is controlled by the target value the operator enters. Example: if sender has 200m and should keep about 10m, run the transfer target as about 190m.

## Remote Control Protocol To Implement In AutoAuction

The API side is ready. AutoAuction now needs to send the runtime data.

API to mod websocket:

- `request_screenshot`

Expected mod response:

```json
{
  "type": "client_screenshot",
  "imageMime": "image/jpeg",
  "imageBase64": "<base64 image>",
  "capturedAt": "2026-06-23T00:00:00.000Z"
}
```

Mod event/log message:

```json
{
  "type": "client_log",
  "level": "info",
  "message": "Handoff complete, new account is Username"
}
```

Valid log levels are `debug`, `info`, `warn`, and `error`. Do not include API keys, Discord webhooks, proxy credentials, launcher tokens, or other secrets in `client_log`.

Important AutoAuction events that should emit `client_log`:

- mod websocket authenticated
- account handoff started
- handoff complete and active username
- transfer invite accepted
- transfer started
- transfer stopped/cancelled
- buy order created
- instant sell completed
- sell order created
- sender instant-buy completed
- transfer cycle complete
- receiver purse before/after/delta
- Bazaar/menu stuck state
- transfer error and recovery cleanup result

## Verification

Latest local verification before this handoff:

```powershell
.\gradlew.bat --no-daemon test --tests com.autoauction.client.transfer.EnderChestParkingPlanTest --tests com.autoauction.client.transfer.BazaarTransferWorkflowTest
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

All passed. The full build produced `autoauction-1.0.0.jar` and it was copied to all three active Prism instances.
Latest copied jar SHA-256 was `F6C7DFA3383880E229A891C358922A93510AF25707E20F2495C47CBAED8EA8A4`.

## Build And Copy

```powershell
cd "C:\Humane\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat build
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test(1)\minecraft\mods\autoauction-1.0.0.jar" -Force
```

## Next Work

Immediate next slice:

1. In `ModSocketClient`, handle incoming `request_screenshot`.
2. Capture the Minecraft framebuffer on the render/client thread.
3. Encode the screenshot as JPEG or PNG base64.
4. Send `client_screenshot` on the existing authenticated websocket.
5. Add a small `sendClientLog(level, message)` helper that strips/avoids secrets.
6. Add focused tests for:
   - `request_screenshot` dispatch
   - `client_screenshot` payload formatting
   - `client_log` helper not logging API keys
7. Build and copy the jar to all three active Prism mod folders.
8. Test from dashboard:
   - open an account card with `Connect`
   - verify URL is `/remote/<minecraft_uuid>`
   - click `Refresh`
   - verify screenshot appears
   - trigger one transfer/handoff event and verify it appears in In-game Logs

After screenshot/logs work:

1. Add dashboard Send Action protocol support in the API.
2. Add AutoAuction handling for remote server command, normal chat message, and client-side mod action.
3. Add better transfer recovery if a Bazaar menu changes or a cycle stalls.
4. Add partial-stack inventory splitting if exact quantities smaller than a full-stack multiple are needed.
5. Continue testing Bazaar product ID resolution with more item families.
6. Add dashboard visibility for reconnects and live transfer session state.
7. Commit any future runtime-flow changes immediately after verification.
