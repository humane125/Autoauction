# Reviewed Multi-Repo Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct eleven validated runtime defects across Test API, Alt Manager, and AutoAuction without introducing new Hypixel UI assumptions.

**Architecture:** Each repository receives an independent test-first patch and commit. Existing public protocols remain unchanged; fixes are internal validation, state preservation, and failure containment.

**Tech Stack:** Node.js/CommonJS/SQLite/ws; Java 21/Fabric/Gradle/JUnit.

## Global Constraints

- Preserve existing commands and WebSocket payloads.
- Do not change the three deferred behaviors requiring user evidence or policy.
- Write and run a failing regression test before production edits.
- Commit each repository independently.

---

### Task 1: Test API correctness

**Files:**
- Modify: `C:/Humane/Hypixel/Test API/server.js`
- Modify: `C:/Humane/Hypixel/Test API/auth-db.js`
- Test: `C:/Humane/Hypixel/Test API/tests/server-auth.test.js`
- Test: `C:/Humane/Hypixel/Test API/tests/auth-db.test.js`

**Interfaces:**
- Preserve `createAuctionIndexService`, `reconcileMinecraftAccountAuctionSnapshots`, `recordMinecraftAccountAuctionCollection`, and dashboard status JSON shapes.

- [ ] Add failing tests for partial-page refresh retention, deleted-account stats, release stream replacement, price-aware collection matching, and timed-ban metadata.
- [ ] Run focused Node tests and confirm each new assertion fails for the reviewed reason.
- [ ] Require complete page refreshes, contain stale-account and stream errors, prefer compatible prices, and forward ban metadata.
- [ ] Run `npm test` and confirm all tests pass.
- [ ] Commit as `fix: harden api state reconciliation`.

### Task 2: Alt Manager lifecycle correctness

**Files:**
- Modify: `C:/Humane/Hypixel/Minecraft Mod/Alt Manager/src/client/java/cc/kamshi/client/screen/AltManagerScreen.java`
- Modify: `C:/Humane/Hypixel/Minecraft Mod/Alt Manager/src/client/java/com/altmanager/client/account/AccountSwitcher.java`
- Modify: `C:/Humane/Hypixel/Minecraft Mod/Alt Manager/src/client/java/cc/kamshi/utility/player/MicrosoftLoginUtility.java`
- Modify: `C:/Humane/Hypixel/Minecraft Mod/Alt Manager/src/client/java/com/altmanager/client/proxy/AccountProxyService.java`
- Test: corresponding files under `src/test/java`

**Interfaces:**
- Preserve UI controls, schedule JSON format, and `AccountSwitcher.switchTo(String)` signature.

- [ ] Add failing tests for compact Stop preservation, WAIT resume, rejected login starts, and exceptional proxy cleanup.
- [ ] Run focused Gradle tests and confirm the reviewed failures.
- [ ] Preserve schedule runtime fields, return accepted-login status, and clear stale proxies on lookup failure.
- [ ] Run `.\gradlew.bat --no-daemon test` and build.
- [ ] Commit as `fix: preserve scheduler and account switch state`.

### Task 3: AutoAuction connection and transfer progress

**Files:**
- Modify: `C:/Humane/Hypixel/Minecraft Mod/26.1.1/src/client/java/com/autoauction/client/control/ModSocketClient.java`
- Modify: `C:/Humane/Hypixel/Minecraft Mod/26.1.1/src/client/java/com/autoauction/client/AutoauctionClient.java`
- Test: `C:/Humane/Hypixel/Minecraft Mod/26.1.1/src/test/java/com/autoauction/client/control/ModSocketClientTest.java`
- Test: `C:/Humane/Hypixel/Minecraft Mod/26.1.1/src/test/java/com/autoauction/client/AutoauctionClientTest.java`

**Interfaces:**
- Capture connection generation in the listener without changing WebSocket payloads.
- Extract a package-visible positive-progress predicate for focused testing.

- [ ] Add failing tests for stale-generation text frames and zero-delta claim summaries.
- [ ] Run focused Gradle tests and confirm both fail for the reviewed reason.
- [ ] Guard text-frame delivery by generation/connection identity and require positive purse delta.
- [ ] Run `.\gradlew.bat --no-daemon test` and build.
- [ ] Commit as `fix: reject stale socket and purse state`.

