# AutoAuction Handoff

Date: 2026-06-19
Branch: `main`
Latest local commit before this handoff: `f127306 Add Prism account import fallback`

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
- Transfer debug messages were made clearer with workflow, state, item, quantity, and delay details.
- Bazaar estimate math was fixed: Hypixel `quick_status.sellPrice` is the receiver buy cost side and `quick_status.buyPrice` is the receiver sell revenue side.
- Bazaar product ID resolution was hardened:
  - Exact product ID match first.
  - Aliases for known mismatches such as `NETHER_WART_DISTILLATE -> NETHER_STALK_DISTILLATE`.
  - Ordered-token fallback for cases like `Glowstone Distillate -> GLOWSTONE_DUST_DISTILLATE`.
  - Ambiguous matches do not guess.
- Latest jars were copied to both listed Prism mod folders after build.
- The 26.1.2 Prism runtime configs were manually updated to `https://humane-hypixel.duckdns.org`.

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

## Verification

Latest local verification before this handoff:

```powershell
.\gradlew.bat --no-daemon test --tests com.autoauction.client.transfer.EnderChestParkingPlanTest --tests com.autoauction.client.transfer.BazaarTransferWorkflowTest
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

All passed. The full build produced `autoauction-1.0.0.jar` and it was copied to all three active Prism instances.

## Build And Copy

```powershell
cd "C:\Humane\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat build
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test(1)\minecraft\mods\autoauction-1.0.0.jar" -Force
```

## Next Work

1. Add better transfer recovery if a Bazaar menu changes or a cycle stalls.
2. Add partial-stack inventory splitting if exact quantities smaller than a full-stack multiple are needed.
3. Continue testing Bazaar product ID resolution with more item families.
4. Add dashboard visibility for reconnects and live transfer session state.
5. Commit any future runtime-flow changes immediately after verification.
