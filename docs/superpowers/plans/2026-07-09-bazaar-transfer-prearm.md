# Bazaar Transfer Prearm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Bazaar money transfer faster by having the sender open and wait in the right Bazaar screen before the receiver's order-ready signal arrives, so the ready signal only triggers the final click.

**Architecture:** Keep the API protocol mostly unchanged. Use the existing sender `transfer_run_sent` event to begin pre-arming instant sell, and keep `transfer_buy_order_ready` as the final-click signal. For the sell-offer side, pre-open the sender to the Bazaar item page while the receiver is preparing the sell offer, then run the instant-buy flow as soon as `transfer_sell_offer_ready` arrives; deeper instant-buy prearming should be behind a later opt-in because it may target the wrong live order book if prepared before the receiver's sell offer exists.

**Tech Stack:** Fabric client mod Java, existing `ModSocketClient` WebSocket protocol, existing `BazaarTransferWorkflow` screen detectors, JUnit tests.

## Global Constraints

- Do not change Bazaar transfer command syntax.
- Do not change accepted sender/receiver roles.
- Do not add a new API protocol message unless the existing `transfer_run_sent`, `transfer_buy_order_ready`, and `transfer_sell_offer_ready` messages cannot cover the flow.
- Sender must never click `Sell Instantly` before the receiver buy order is ready.
- Sender must never confirm instant buy before the receiver sell offer is ready.
- If a screen timeout, account switch, disconnect, or transfer cancel happens, clear all prearmed workflow state.
- Keep debug output behind the existing AutoAuction debug flag.

---

## File Structure

- Modify `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`
  - Add prearm states to `SenderInstantSellWorkflow`.
  - Add sender-side start from `transfer_run_sent`.
  - Add a ready-signal method called by `transfer_buy_order_ready`.
  - Add sender-side item-page prearm before `transfer_sell_offer_ready`.
- Modify `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\transfer\BazaarTransferWorkflow.java`
  - Add pure helper names for prearm states if needed.
  - Keep existing title and chat matching behavior.
- Modify `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\control\ModSocketClient.java`
  - Only if current handler does not expose enough context to start prearm from `transfer_run_sent`.
- Modify tests under `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\test\java\com\autoauction\client`
  - Prefer pure helper tests for state transitions where possible.
  - Keep socket tests focused on no protocol regressions.

---

### Task 1: Sender Instant Sell Prearm State

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`
- Test: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\test\java\com\autoauction\client\transfer\SenderPrearmStateTest.java`

**Interfaces:**
- Consumes: existing `SenderInstantSellWorkflow(String itemName, int quantity)`.
- Produces: `SenderInstantSellWorkflow.armOnly()` and `SenderInstantSellWorkflow.markBuyOrderReady()`.

- [ ] **Step 1: Add a failing pure state test**

Create `SenderPrearmStateTest` with a small helper enum/transition assertion:

```java
@Test
void sellPrearmWaitsForReadyBeforeFinalClick() {
	SenderPrearmGate gate = new SenderPrearmGate();
	assertFalse(gate.canFinalClick());
	gate.markScreenArmed();
	assertFalse(gate.canFinalClick());
	gate.markReadySignal();
	assertTrue(gate.canFinalClick());
}
```

Expected failing reason before implementation: `SenderPrearmGate` does not exist.

- [ ] **Step 2: Implement the minimal gate**

Add a package-private helper near transfer code:

```java
final class SenderPrearmGate {
	private boolean screenArmed;
	private boolean readySignal;

	void markScreenArmed() {
		screenArmed = true;
	}

	void markReadySignal() {
		readySignal = true;
	}

	boolean canFinalClick() {
		return screenArmed && readySignal;
	}
}
```

- [ ] **Step 3: Run the focused test**

Run:

```powershell
cd "C:\Humane\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat --no-daemon test --tests com.autoauction.client.transfer.SenderPrearmStateTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add src/test/java/com/autoauction/client/transfer/SenderPrearmStateTest.java src/client/java/com/autoauction/client/AutoauctionClient.java
git commit -m "test: add sender transfer prearm gate"
```

---

### Task 2: Start Sell Prearm From `transfer_run_sent`

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`

**Interfaces:**
- Consumes: existing `onRunSent` handling.
- Produces: sender opens `/bz <item>`, reaches item page, and waits before clicking `SELL_INSTANTLY_SLOT`.

- [ ] **Step 1: Change sender run-sent handler**

In the `transfer_run_sent` branch, after role/session validation, create a sender instant-sell workflow in prearm mode:

```java
senderInstantSellWorkflow = new SenderInstantSellWorkflow(session.itemName(), run.quantity(), true);
senderInstantSellWorkflow.start(Minecraft.getInstance());
```

- [ ] **Step 2: Update constructor**

Change `SenderInstantSellWorkflow` to accept `boolean prearmOnly`:

```java
private SenderInstantSellWorkflow(String itemName, int quantity, boolean prearmOnly) {
	this.itemName = itemName;
	this.quantity = Math.max(1, quantity);
	this.prearmOnly = prearmOnly;
	this.prearmGate = prearmOnly ? new SenderPrearmGate() : null;
}
```

Keep a delegating constructor for the old immediate path:

```java
private SenderInstantSellWorkflow(String itemName, int quantity) {
	this(itemName, quantity, false);
}
```

- [ ] **Step 3: Wait at item page**

When `SenderInstantSellWorkflow` reaches the item page and would normally click `SELL_INSTANTLY_SLOT`, if `prearmOnly` is true:

```java
prearmGate.markScreenArmed();
transition(SenderInstantSellState.WAIT_BUY_ORDER_READY, client, 0);
return;
```

In `WAIT_BUY_ORDER_READY`, do nothing until `markBuyOrderReady()` is called.

- [ ] **Step 4: Handle early ready signal**

Add:

```java
private void markBuyOrderReady(Minecraft client) {
	if (prearmGate != null) {
		prearmGate.markReadySignal();
	}
	if (state == SenderInstantSellState.WAIT_BUY_ORDER_READY || (prearmGate != null && prearmGate.canFinalClick())) {
		transition(SenderInstantSellState.CLICK_SELL_INSTANTLY, client, 0);
	}
}
```

- [ ] **Step 5: Commit**

```powershell
git add src/client/java/com/autoauction/client/AutoauctionClient.java
git commit -m "feat: prearm sender instant sell during transfer"
```

---

### Task 3: Make Buy-Order-Ready Trigger Final Sell Click

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`

**Interfaces:**
- Consumes: existing `transfer_buy_order_ready` socket handler.
- Produces: if sell workflow is already prearmed, final sell click happens immediately; otherwise fallback starts normal sell workflow.

- [ ] **Step 1: Update `transfer_buy_order_ready` handling**

Replace unconditional workflow creation with:

```java
if (senderInstantSellWorkflow != null && senderInstantSellWorkflow.matches(session.itemName(), run.quantity())) {
	senderInstantSellWorkflow.markBuyOrderReady(Minecraft.getInstance());
} else {
	senderInstantSellWorkflow = new SenderInstantSellWorkflow(session.itemName(), run.quantity());
	senderInstantSellWorkflow.start(Minecraft.getInstance());
}
```

- [ ] **Step 2: Add `matches` helper**

```java
private boolean matches(String expectedItemName, int expectedQuantity) {
	return itemName.equalsIgnoreCase(expectedItemName) && quantity == Math.max(1, expectedQuantity);
}
```

- [ ] **Step 3: Verify fallback behavior**

Run:

```powershell
cd "C:\Humane\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat --no-daemon test
```

Expected: all existing tests pass.

- [ ] **Step 4: Commit**

```powershell
git add src/client/java/com/autoauction/client/AutoauctionClient.java
git commit -m "feat: trigger prearmed transfer sell on ready signal"
```

---

### Task 4: Preopen Sender Bazaar Item Page Before Instant Buy

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`

**Interfaces:**
- Consumes: sender instant sell completion.
- Produces: sender is already on the Bazaar item page while receiver builds the sell offer.

- [ ] **Step 1: Add a lightweight item-page prearm workflow**

Add `SenderItemPagePrearmWorkflow` that:

```java
actions.sendChatCommand(client, "/bz " + itemName);
```

Then waits until:

```java
BazaarTransferWorkflow.isItemPage(actions.currentScreenTitle(client), itemName)
```

When ready, it stops and leaves the screen open.

- [ ] **Step 2: Start it after sender instant sell is complete**

After successful sender instant sell, before waiting for receiver sell offer:

```java
senderItemPagePrearmWorkflow = new SenderItemPagePrearmWorkflow(itemName);
senderItemPagePrearmWorkflow.start(client);
```

- [ ] **Step 3: Stop prearm when sell offer ready arrives**

In `transfer_sell_offer_ready`, clear the prearm workflow and start `SenderInstantBuyWorkflow` normally. Since the item page is already open, the existing instant-buy workflow should detect the page quickly and continue from there.

- [ ] **Step 4: Commit**

```powershell
git add src/client/java/com/autoauction/client/AutoauctionClient.java
git commit -m "feat: preopen sender item page before transfer instant buy"
```

---

### Task 5: Cancellation, Errors, and Account Switch Cleanup

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`

**Interfaces:**
- Consumes: existing transfer cancel/error/account-change cleanup paths.
- Produces: no stale prearmed menus or old ready signals can fire after cancel.

- [ ] **Step 1: Clear prearm state in all transfer cleanup paths**

Set these to null wherever transfer cancel/error/session reset currently clears workflows:

```java
senderInstantSellWorkflow = null;
senderItemPagePrearmWorkflow = null;
senderInstantBuyWorkflow = null;
```

- [ ] **Step 2: Validate username/session before final click**

Before `CLICK_SELL_INSTANTLY`, verify current username still matches the session sender. If not, fail and notify:

```java
fail(client, "sender account changed before prearmed instant sell could click");
```

- [ ] **Step 3: Commit**

```powershell
git add src/client/java/com/autoauction/client/AutoauctionClient.java
git commit -m "fix: clear transfer prearm state on cancel and account changes"
```

---

### Task 6: Manual Verification

**Files:**
- No source changes unless a bug is found.

**Interfaces:**
- Consumes: built AutoAuction jar.
- Produces: confirmed faster transfer handoff.

- [ ] **Step 1: Build**

```powershell
cd "C:\Humane\Hypixel\Minecraft Mod\26.1.1"
.\gradlew.bat --no-daemon build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Copy jar to test instances**

Copy the built AutoAuction jar to:

```text
C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\mods
C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test\minecraft\mods
C:\Users\SoulP\AppData\Roaming\PrismLauncher\instances\26.1.2 test(1)\minecraft\mods
```

- [ ] **Step 3: Test transfer timing**

Use two accounts:

```text
/mf pair <receiver> <item>
/mf accept <sender>
/mf run <targetCoins>
```

Expected:

- Sender opens Bazaar item page before receiver buy order is ready.
- Sender clicks sell instantly immediately after receiver ready message.
- Sender opens Bazaar item page again while receiver prepares sell offer.
- Sender begins instant-buy flow immediately when sell offer ready arrives.
- Cancel/error clears the prepared state.

---

## Self-Review

- Spec coverage: Covers sell prearm, buy-side preopen, cancellation, role safety, and manual verification.
- Placeholder scan: No unresolved placeholders.
- Type consistency: Introduces `SenderPrearmGate`, `markBuyOrderReady`, `matches`, and `SenderItemPagePrearmWorkflow`; all are consumed by later tasks.
