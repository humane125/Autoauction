# AutoAuction Handoff

Date: 2026-06-18
Branch: `main`
Latest local commit before this handoff: `e18d99d Resolve bazaar product id aliases`

## Current Setup

- Local repo: `C:\Projects\Hypixel\Minecraft Mod\26.1.1`
- GitHub remote: `https://github.com/humane125/Autoauction.git`
- Current public API URL: `https://humane-hypixel.duckdns.org`
- Old ngrok URL is deprecated: `https://lazy-similarly-reaffirm.ngrok-free.dev`
- Built jar: `build\libs\autoauction-1.0.0.jar`
- Active Prism mod folders:
  - `C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods`
  - `C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2 rivoh\minecraft\mods`

Do not commit API tokens, Discord webhooks, local Prism configs, generated `logs/`, or Minecraft account data.

## What Happened

- Transfer automation was completed beyond the old buy-order-only handoff.
- Sender now instant-sells into the receiver buy order, then instant-buys the receiver sell offer.
- Receiver now claims the filled buy order, creates the sell offer, waits for sender buy-back, claims coins, and sends `transfer_cycle_complete`.
- Transfer target loops now continue until the receiver-reported purse delta reaches or exceeds the requested target.
- Final target output now reports receiver starting purse, done purse, and total delta.
- Receiver claim delta waits for purse update to avoid stale scoreboard/negative delta.
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

## Verification

Latest local verification before this handoff:

```powershell
.\gradlew.bat test --tests com.autoauction.client.transfer.BazaarTransferEstimateTest
.\gradlew.bat build
```

Both passed. The full build produced `autoauction-1.0.0.jar` and it was copied to both active Prism instances.

## Build And Copy

```powershell
cd "C:\Projects\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat build
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "build\libs\autoauction-1.0.0.jar" "C:\Users\moham\AppData\Roaming\PrismLauncher\instances\26.1.2 rivoh\minecraft\mods\autoauction-1.0.0.jar" -Force
```

## Next Work

1. Add better transfer recovery if a Bazaar menu changes or a cycle stalls.
2. Add configurable transfer quantity, max cycles, margin threshold, and safety controls.
3. Continue testing Bazaar product ID resolution with more item families.
4. Add dashboard visibility for reconnects and live transfer session state.
5. Commit any future runtime-flow changes immediately after verification.
