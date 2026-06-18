# AutoAuction Handoff

Date: 2026-06-18
Branch: `main`

## Current Setup

- Local repo: `C:\Humane\Hypixel\Minecraft Mod\26.1.1`
- GitHub remote: `https://github.com/humane125/Autoauction.git`
- Public API URL: `https://lazy-similarly-reaffirm.ngrok-free.dev`
- Built jar: `build\libs\autoauction-1.0.0.jar`
- Prism live mods folder: `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods`
- Prism test mods folder: `C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods`

Do not commit real API tokens, Discord webhooks, Discord user IDs, local configs, or `logs/`.

## Current Behavior

- AutoAuction owns auction listing, macro handoff orchestration, and the connected transfer client commands.
- Alt Manager owns account switching and proxy fetch.
- API websocket status uses `active`, `hypixel`, `offline`, and `banned`.
- Start/stop macro commands are client-side mod commands.
- Real listing mode asks the API for recommended BIN pricing.
- Before taking off armor, the mod sends `/is` and waits 5.5 seconds.
- If inventory has no empty slot, the mod opens `/bz`, clicks `Sell Inventory Now`, confirms selling the whole inventory, then retries inventory-space checks.
- After listing armor, AutoAuction asks Alt Manager to switch to the next account, waits for proxy readiness, waits 1.5 seconds, reconnects to Hypixel, waits for player/server availability, then can send the configured macro start command.

## Connected Transfer Commands

- `/autoauction transfer list` shows currently connected mod clients from the API.
- `/autoauction transfer <receiverUsername> <itemName...>` invites a connected receiver. The current account is the sender.
- `/autoauction transfer accept <senderUsername>` accepts an invite.
- `/autoauction transfer decline <senderUsername>` declines an invite.
- `/autoauction transfer cancel` cancels the current pending or accepted session.
- `/autoauction transfer run` starts the current accepted transfer automation.

The item argument is greedy, so names like `ENCHANTED DIAMOND` and `Blaze Rod Distillate` are accepted.

## Transfer Automation State

Implemented flow:

1. Sender runs `/autoauction transfer run`.
2. Sender scans its inventory for the configured item and sends the quantity to the receiver.
3. Receiver opens `/bz <item>`, clicks the item result in `Bazaar -> "<item>"`, opens the item page, creates a buy order, chooses `Custom Amount`, submits the sender quantity, chooses `Top Order +0.1`, and confirms the buy order.
4. Receiver notifies the sender through the API with `transfer_buy_order_ready`.
5. Sender opens `/bz <item>`, clicks the item result, opens the item page, clicks `Sell Instantly`, handles the optional instant-sell warning by waiting 6 seconds, confirms, then closes the Bazaar menu.
6. Receiver listens for the filled buy-order chat message. Minecraft color codes are stripped before matching messages like `[Bazaar] Your Buy Order for 1x Blaze Rod Distillate was filled!`.
7. Receiver opens `/bz`, clicks `Manage Orders`, finds the buy order for the configured item, claims it, closes Bazaar, opens `/bz <item>`, creates a sell offer, chooses `Best Offer -0.1`, and confirms the sell offer.

Menu matching supports:

- Search result titles shaped like `Bazaar -> "<item>"`
- Grouped or truncated item pages shaped like `<category> -> <item prefix>`, for example `Inferno Demonlord -> Blaze Rod D`
- Truncated item names by matching normalized prefixes against the configured item words

## Known Limits

- The transfer flow does not yet loop automatically after the receiver creates the sell offer.
- The sender does not yet buy the receiver sell offer back.
- Sell-offer fill detection and sell-offer claim handling are not implemented yet.
- Quantity, margin limits, cycle count, and stop-loss controls are not configurable yet.
- Failure recovery is basic; if a menu appears in a new shape, the workflow reports a failure and stops.
- Websocket reconnect/backoff after API or ngrok restarts is still needed.

## Verification

Run locally:

```powershell
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

Latest local verification on 2026-06-18:

- `.\gradlew.bat --no-daemon test` passed.
- `.\gradlew.bat --no-daemon build` passed.

## Build And Copy

```powershell
.\gradlew.bat --no-daemon build
Copy-Item "C:\Humane\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods\autoauction-1.0.0.jar" -Force
Copy-Item "C:\Humane\Hypixel\Minecraft Mod\26.1.1\build\libs\autoauction-1.0.0.jar" "C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods\autoauction-1.0.0.jar" -Force
```

Latest jar was copied to both Prism instances on 2026-06-18.

## Next Work

1. Implement sender buy-back from the receiver sell offer.
2. Add receiver sell-offer fill detection and claim handling.
3. Add automatic transfer cycling until configured stop conditions are reached.
4. Add configurable quantity, max cycles, margin threshold, and coin safety checks.
5. Add websocket reconnect/backoff and resend current status after reconnect.
6. Test more item families with grouped and truncated Bazaar pages.
