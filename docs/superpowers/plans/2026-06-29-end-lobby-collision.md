# End Lobby Collision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build automatic lobby switching when a macro account enters an End lobby containing another registered account.

**Architecture:** The API broadcasts registered account usernames to mod sockets. The AutoAuction mod keeps a local username registry and uses a focused lobby-collision controller to decide when to run a small macro-off, `/is`, macro-on workflow.

**Tech Stack:** Fabric client mod Java, Node `ws`, existing AutoAuction socket protocol, JUnit, Node test runner.

---

### Task 1: API Registered Account Broadcast

**Files:**
- Modify: `C:\Humane\Hypixel\Test API\server.js`
- Test: `C:\Humane\Hypixel\Test API\tests\server-auth.test.js`

- [ ] Add a failing API websocket test proving a mod socket receives `registered_accounts` with all Minecraft usernames.
- [ ] Implement `registeredAccounts()` in the mod connection registry from `listMinecraftAccounts(db)`.
- [ ] Send `registered_accounts` after mod authentication and when account lists change.
- [ ] Run `npm test`.
- [ ] Commit API changes.

### Task 2: Mod Socket Parsing

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\control\ModSocketClient.java`
- Test: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\test\java\com\autoauction\client\control\ModSocketClientTest.java`

- [ ] Add a failing test for `registered_accounts` dispatch.
- [ ] Add a `RegisteredAccountsHandler` and username record/parser.
- [ ] Run the focused socket test.
- [ ] Commit mod socket parsing changes.

### Task 3: Collision Controller

**Files:**
- Create: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\lobby\LobbyCollisionController.java`
- Test: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\test\java\com\autoauction\client\lobby\LobbyCollisionControllerTest.java`

- [ ] Add failing tests for The End gating, current account exclusion, registered-name matching, command order, and cooldown.
- [ ] Implement the minimal controller state machine.
- [ ] Run the focused controller test.
- [ ] Commit controller changes.

### Task 4: AutoAuction Integration

**Files:**
- Modify: `C:\Humane\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`
- Test: existing mod test suite.

- [ ] Wire registered account updates into the controller.
- [ ] Build tablist/status snapshots each tick and call the controller after Nebula state updates.
- [ ] Use existing `sendCommand` behavior for `/is` and macro toggle commands.
- [ ] Run `.\gradlew.bat --no-daemon test`.
- [ ] Build and copy the jar to Prism instances.
- [ ] Commit integration changes.
