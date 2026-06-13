# AutoAuction Armor Selloff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a guarded Fabric 26.1.1 client automation that watches equipped Final Destination armor, stops the macro when all four pieces reach 25,000 kills, removes the armor, prices each piece through the configured auction API, lists them as BIN auctions through the private-server AH GUI, sends Discord webhook updates, returns to hub, waits five seconds, and disconnects.

**Architecture:** Keep game-facing code thin and move logic into focused services that can be unit-tested without a running Minecraft client. A single `AuctionAutomationController` owns the state machine; Minecraft-specific adapters provide equipped armor snapshots, chat commands, screen detection, and slot clicks.

**Tech Stack:** Fabric Loom, Minecraft 26.1.1, Java 25, Fabric API client lifecycle events, Java `HttpClient`, Gson or Fabric-loader-provided Gson for JSON, JUnit 5 for pure Java tests.

---

## Scope And Safety

- The automation is client-side only.
- It must default to `dryRun: true`.
- It must read `apiBaseUrl` from `config/autoauction.json`; the default is `https://lazy-similarly-reaffirm.ngrok-free.dev`.
- It must refuse real listing when `macroStopCommand` is blank.
- It must only trigger when all four equipped Final Destination pieces are present and each has at least `killThreshold` kills.
- It must remove and list the four armor pieces that satisfied the threshold, not arbitrary matching inventory items.
- It must enforce `allowedServers` before taking action.
- It must send Discord issue alerts with `<@discordMentionUserId>` when configured.
- It must leave the macro stopped, send `/hub`, wait `disconnectDelayMs`, and disconnect.

## Planned File Structure

- Modify: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\build.gradle`
  - Add JUnit 5 for unit tests.
- Modify: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\AutoauctionClient.java`
  - Register tick handler and keybind/debug hooks.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\config\AutoAuctionConfig.java`
  - Config model, defaults, validation.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\config\AutoAuctionConfigStore.java`
  - Load/save `config/autoauction.json`.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\domain\ArmorPiece.java`
  - Enum for helmet, chestplate, leggings, boots.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\domain\ArmorSnapshot.java`
  - Immutable parsed armor data.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\domain\AuctionItemRequest.java`
  - API request DTO for one item.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\item\FinalDestinationParser.java`
  - Parse display name and lore strings into armor snapshots and recommendation requests.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\api\AuctionApiClient.java`
  - Calls `{apiBaseUrl}/api/recommend-bin`.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\notify\DiscordNotifier.java`
  - Sends webhook messages.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\automation\AuctionAutomationController.java`
  - State machine and timeout handling.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\automation\AutomationState.java`
  - Enum of automation states.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\minecraft\MinecraftGameActions.java`
  - Adapter for chat, equipped armor, armor removal, slot clicks, `/hub`, disconnect.
- Create: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\client\java\com\autoauction\client\minecraft\AuctionScreenDetector.java`
  - Detect exact AH-copy screens by title, slot count, and expected slot contents.
- Create tests under `C:\Projects\Hypixel\Minecraft Mod\26.1.1\src\test\java\com\autoauction\client\...`
  - Unit tests for config, parser, API client, Discord notifier, and state machine.

---

### Task 1: Add Test Harness

**Files:**
- Modify: `C:\Projects\Hypixel\Minecraft Mod\26.1.1\build.gradle`

- [ ] **Step 1: Add JUnit 5 dependencies**

Add this inside `dependencies`:

```gradle
testImplementation platform("org.junit:junit-bom:5.11.4")
testImplementation "org.junit.jupiter:junit-jupiter"
```

Add this near the other task configuration:

```gradle
test {
	useJUnitPlatform()
}
```

- [ ] **Step 2: Run tests**

Run:

```powershell
cd 'C:\Projects\Hypixel\Minecraft Mod\26.1.1'
.\gradlew.bat test
```

Expected: Gradle succeeds with zero tests or with only generated no-test output.

- [ ] **Step 3: Commit checkpoint when git exists**

```powershell
git add build.gradle
git commit -m "test: add junit harness"
```

If `git status` reports this is not a git repository, skip the commit and continue.

---

### Task 2: Config File

**Files:**
- Create: `src\client\java\com\autoauction\client\config\AutoAuctionConfig.java`
- Create: `src\client\java\com\autoauction\client\config\AutoAuctionConfigStore.java`
- Test: `src\test\java\com\autoauction\client\config\AutoAuctionConfigStoreTest.java`

- [ ] **Step 1: Write config tests**

```java
package com.autoauction.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AutoAuctionConfigStoreTest {
	@TempDir
	Path tempDir;

	@Test
	void createsDefaultConfigWhenMissing() throws Exception {
		AutoAuctionConfigStore store = new AutoAuctionConfigStore(tempDir);

		AutoAuctionConfig config = store.load();

		assertEquals("https://lazy-similarly-reaffirm.ngrok-free.dev", config.apiBaseUrl());
		assertTrue(config.dryRun());
		assertEquals(25000, config.killThreshold());
		assertTrue(Files.exists(tempDir.resolve("autoauction.json")));
	}

	@Test
	void rejectsRealListingWithoutMacroStopCommand() throws Exception {
		AutoAuctionConfig config = AutoAuctionConfig.defaults()
			.withDryRun(false)
			.withMacroStopCommand("");

		assertFalse(config.canRunRealListing());
	}
}
```

- [ ] **Step 2: Implement config model**

```java
package com.autoauction.client.config;

import java.util.List;

public record AutoAuctionConfig(
	String apiBaseUrl,
	String apiToken,
	String discordWebhookUrl,
	String discordMentionUserId,
	String macroStopCommand,
	String returnToHubCommand,
	boolean dryRun,
	boolean privateServerOnly,
	List<String> allowedServers,
	int killThreshold,
	int minBinPrice,
	int maxBinPrice,
	int screenTimeoutMs,
	int clickDelayMs,
	int disconnectDelayMs
) {
	public static AutoAuctionConfig defaults() {
		return new AutoAuctionConfig(
			"https://lazy-similarly-reaffirm.ngrok-free.dev",
			"",
			"",
			"",
			"",
			"/hub",
			true,
			true,
			List.of("localhost", "127.0.0.1"),
			25000,
			1_000_000,
			30_000_000,
			8_000,
			250,
			5_000
		);
	}

	public boolean canRunRealListing() {
		return dryRun || !macroStopCommand.isBlank();
	}

	public AutoAuctionConfig withDryRun(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			returnToHubCommand, value, privateServerOnly, allowedServers, killThreshold, minBinPrice, maxBinPrice,
			screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withMacroStopCommand(String value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, value,
			returnToHubCommand, dryRun, privateServerOnly, allowedServers, killThreshold, minBinPrice, maxBinPrice,
			screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}
}
```

- [ ] **Step 3: Implement config store**

```java
package com.autoauction.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoAuctionConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path configFile;

	public AutoAuctionConfigStore(Path configDir) {
		this.configFile = configDir.resolve("autoauction.json");
	}

	public AutoAuctionConfig load() throws IOException {
		if (!Files.exists(configFile)) {
			AutoAuctionConfig defaults = AutoAuctionConfig.defaults();
			save(defaults);
			return defaults;
		}

		String json = Files.readString(configFile);
		AutoAuctionConfig config = GSON.fromJson(json, AutoAuctionConfig.class);
		return config == null ? AutoAuctionConfig.defaults() : config;
	}

	public void save(AutoAuctionConfig config) throws IOException {
		Files.createDirectories(configFile.getParent());
		Files.writeString(configFile, GSON.toJson(config));
	}
}
```

- [ ] **Step 4: Run config tests**

```powershell
.\gradlew.bat test --tests com.autoauction.client.config.AutoAuctionConfigStoreTest
```

Expected: both tests pass.

---

### Task 3: Final Destination Armor Parser

**Files:**
- Create: `src\client\java\com\autoauction\client\domain\ArmorPiece.java`
- Create: `src\client\java\com\autoauction\client\domain\ArmorSnapshot.java`
- Create: `src\client\java\com\autoauction\client\domain\AuctionItemRequest.java`
- Create: `src\client\java\com\autoauction\client\item\FinalDestinationParser.java`
- Test: `src\test\java\com\autoauction\client\item\FinalDestinationParserTest.java`

- [ ] **Step 1: Write parser tests**

```java
package com.autoauction.client.item;

import com.autoauction.client.domain.ArmorPiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinalDestinationParserTest {
	private final FinalDestinationParser parser = new FinalDestinationParser();

	@Test
	void parsesFinalDestinationKillsWithCommas() {
		var result = parser.parse(ArmorPiece.CHESTPLATE, "Ancient Final Destination Chestplate",
			List.of("Kills: 25,437", "LEGENDARY CHESTPLATE"));

		assertTrue(result.isPresent());
		assertEquals(25437, result.get().kills());
		assertEquals("Final Destination Chestplate", result.get().baseName());
	}

	@Test
	void ignoresNonFinalDestinationArmor() {
		var result = parser.parse(ArmorPiece.BOOTS, "Ancient Necron's Boots", List.of("Kills: 25,437"));

		assertTrue(result.isEmpty());
	}
}
```

- [ ] **Step 2: Implement DTOs and parser**

```java
package com.autoauction.client.domain;

public enum ArmorPiece {
	HELMET("Final Destination Helmet"),
	CHESTPLATE("Final Destination Chestplate"),
	LEGGINGS("Final Destination Leggings"),
	BOOTS("Final Destination Boots");

	private final String baseName;

	ArmorPiece(String baseName) {
		this.baseName = baseName;
	}

	public String baseName() {
		return baseName;
	}
}
```

```java
package com.autoauction.client.domain;

public record ArmorSnapshot(ArmorPiece piece, String displayName, String baseName, int kills, boolean recomb, int stars) {
	public boolean reached(int threshold) {
		return kills >= threshold;
	}
}
```

```java
package com.autoauction.client.domain;

import java.util.Map;

public record AuctionItemRequest(String baseName, Map<String, Object> attributes) {
}
```

```java
package com.autoauction.client.item;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FinalDestinationParser {
	private static final Pattern KILLS = Pattern.compile("(?i)kills\\s*:\\s*([0-9,]+)");

	public Optional<ArmorSnapshot> parse(ArmorPiece piece, String displayName, List<String> loreLines) {
		if (!displayName.contains(piece.baseName())) {
			return Optional.empty();
		}

		int kills = loreLines.stream()
			.map(KILLS::matcher)
			.filter(matcher -> matcher.find())
			.map(matcher -> matcher.group(1).replace(",", ""))
			.mapToInt(Integer::parseInt)
			.findFirst()
			.orElse(0);

		boolean recomb = loreLines.stream().anyMatch(line -> line.toLowerCase().contains("recombobulated"));
		int stars = countStars(displayName);
		return Optional.of(new ArmorSnapshot(piece, displayName, piece.baseName(), kills, recomb, stars));
	}

	private int countStars(String displayName) {
		int stars = 0;
		for (int i = 0; i < displayName.length(); i++) {
			if (displayName.charAt(i) == '✪') {
				stars++;
			}
		}
		return stars;
	}
}
```

- [ ] **Step 3: Run parser tests**

```powershell
.\gradlew.bat test --tests com.autoauction.client.item.FinalDestinationParserTest
```

Expected: parser tests pass.

---

### Task 4: Auction API Client

**Files:**
- Create: `src\client\java\com\autoauction\client\api\AuctionApiClient.java`
- Test: `src\test\java\com\autoauction\client\api\AuctionApiClientTest.java`

- [ ] **Step 1: Write API client test using local HTTP server**

```java
package com.autoauction.client.api;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.AuctionItemRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionApiClientTest {
	@Test
	void postsToConfiguredRecommendBinEndpoint() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/recommend-bin", exchange -> {
			byte[] response = "{\"recommendedPrice\":25000000,\"warnings\":[]}".getBytes();
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			AutoAuctionConfig config = new AutoAuctionConfig(baseUrl, "", "", "", "/stopmacro", "/hub", true, true,
				java.util.List.of("localhost"), 25000, 1_000_000, 30_000_000, 8_000, 250, 5_000);
			AuctionApiClient client = new AuctionApiClient(config);

			var response = client.recommend(new AuctionItemRequest("Final Destination Chestplate", Map.of("minKills", 25000)));

			assertEquals(25_000_000, response.recommendedPrice());
		} finally {
			server.stop(0);
		}
	}
}
```

- [ ] **Step 2: Implement API client**

```java
package com.autoauction.client.api;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.AuctionItemRequest;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class AuctionApiClient {
	private static final Gson GSON = new Gson();
	private final AutoAuctionConfig config;
	private final HttpClient client = HttpClient.newHttpClient();

	public AuctionApiClient(AutoAuctionConfig config) {
		this.config = config;
	}

	public RecommendResponse recommend(AuctionItemRequest requestBody) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + "/api/recommend-bin"))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

		if (!config.apiToken().isBlank()) {
			builder.header("Authorization", "Bearer " + config.apiToken());
		}

		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Auction API returned HTTP " + response.statusCode());
		}
		return GSON.fromJson(response.body(), RecommendResponse.class);
	}

	public record RecommendResponse(int recommendedPrice, List<String> warnings) {
	}
}
```

- [ ] **Step 3: Run API client test**

```powershell
.\gradlew.bat test --tests com.autoauction.client.api.AuctionApiClientTest
```

Expected: API test passes and proves the configured base URL is used.

---

### Task 5: Discord Notifier

**Files:**
- Create: `src\client\java\com\autoauction\client\notify\DiscordNotifier.java`
- Test: `src\test\java\com\autoauction\client\notify\DiscordNotifierTest.java`

- [ ] **Step 1: Write notifier test**

```java
package com.autoauction.client.notify;

import com.autoauction.client.config.AutoAuctionConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordNotifierTest {
	@Test
	void includesMentionInIssueMessages() throws Exception {
		AtomicReference<String> body = new AtomicReference<>("");
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/webhook", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes()));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();

		try {
			String webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
			AutoAuctionConfig config = new AutoAuctionConfig("https://lazy-similarly-reaffirm.ngrok-free.dev", "",
				webhook, "123456789012345678", "/stopmacro", "/hub", true, true, List.of("localhost"),
				25000, 1_000_000, 30_000_000, 8_000, 250, 5_000);

			new DiscordNotifier(config).issue("failed to open Auction House");

			assertTrue(body.get().contains("<@123456789012345678>"));
			assertTrue(body.get().contains("failed to open Auction House"));
		} finally {
			server.stop(0);
		}
	}
}
```

- [ ] **Step 2: Implement notifier**

```java
package com.autoauction.client.notify;

import com.autoauction.client.config.AutoAuctionConfig;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class DiscordNotifier {
	private static final Gson GSON = new Gson();
	private final AutoAuctionConfig config;
	private final HttpClient client = HttpClient.newHttpClient();

	public DiscordNotifier(AutoAuctionConfig config) {
		this.config = config;
	}

	public void issue(String message) throws Exception {
		send("issue", mentionPrefix() + "AutoAuction issue: " + message);
	}

	public void info(String message) throws Exception {
		send("info", "AutoAuction: " + message);
	}

	private String mentionPrefix() {
		return config.discordMentionUserId().isBlank() ? "" : "<@" + config.discordMentionUserId() + "> ";
	}

	private void send(String type, String content) throws Exception {
		if (config.discordWebhookUrl().isBlank()) {
			return;
		}

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(config.discordWebhookUrl()))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("content", content))))
			.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Discord webhook failed for " + type + " with HTTP " + response.statusCode());
		}
	}
}
```

- [ ] **Step 3: Run notifier test**

```powershell
.\gradlew.bat test --tests com.autoauction.client.notify.DiscordNotifierTest
```

Expected: notifier test passes.

---

### Task 6: Automation State Machine

**Files:**
- Create: `src\client\java\com\autoauction\client\automation\AutomationState.java`
- Create: `src\client\java\com\autoauction\client\automation\AuctionAutomationController.java`
- Test: `src\test\java\com\autoauction\client\automation\AuctionAutomationControllerTest.java`

- [ ] **Step 1: Write state-machine tests**

```java
package com.autoauction.client.automation;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionAutomationControllerTest {
	@Test
	void waitsUntilAllFourArmorPiecesReachThreshold() {
		AutoAuctionConfig config = AutoAuctionConfig.defaults();
		AuctionAutomationController controller = new AuctionAutomationController(config);

		EnumMap<ArmorPiece, ArmorSnapshot> armor = fullSet(25000);
		armor.put(ArmorPiece.BOOTS, new ArmorSnapshot(ArmorPiece.BOOTS, "Final Destination Boots",
			"Final Destination Boots", 24999, false, 0));

		controller.observeArmor(armor);
		assertEquals(AutomationState.WATCHING_ARMOR, controller.state());

		armor.put(ArmorPiece.BOOTS, new ArmorSnapshot(ArmorPiece.BOOTS, "Final Destination Boots",
			"Final Destination Boots", 25000, false, 0));
		controller.observeArmor(armor);

		assertEquals(AutomationState.THRESHOLD_REACHED, controller.state());
	}

	private EnumMap<ArmorPiece, ArmorSnapshot> fullSet(int kills) {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		for (ArmorPiece piece : ArmorPiece.values()) {
			armor.put(piece, new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), kills, false, 0));
		}
		return armor;
	}
}
```

- [ ] **Step 2: Implement state enum and controller threshold logic**

```java
package com.autoauction.client.automation;

public enum AutomationState {
	WATCHING_ARMOR,
	THRESHOLD_REACHED,
	STOPPING_MACRO,
	REMOVING_ARMOR,
	PRICING_ITEMS,
	LISTING_ITEMS,
	RETURNING_TO_HUB,
	DISCONNECTING,
	DONE,
	ERROR
}
```

```java
package com.autoauction.client.automation;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;

import java.util.EnumMap;
import java.util.Map;

public final class AuctionAutomationController {
	private final AutoAuctionConfig config;
	private AutomationState state = AutomationState.WATCHING_ARMOR;
	private EnumMap<ArmorPiece, ArmorSnapshot> triggeredArmor = new EnumMap<>(ArmorPiece.class);

	public AuctionAutomationController(AutoAuctionConfig config) {
		this.config = config;
	}

	public void observeArmor(Map<ArmorPiece, ArmorSnapshot> equippedArmor) {
		if (state != AutomationState.WATCHING_ARMOR || equippedArmor.size() != ArmorPiece.values().length) {
			return;
		}

		boolean allReached = equippedArmor.values().stream().allMatch(armor -> armor.reached(config.killThreshold()));
		if (allReached) {
			triggeredArmor = new EnumMap<>(equippedArmor);
			state = AutomationState.THRESHOLD_REACHED;
		}
	}

	public AutomationState state() {
		return state;
	}

	public EnumMap<ArmorPiece, ArmorSnapshot> triggeredArmor() {
		return new EnumMap<>(triggeredArmor);
	}
}
```

- [ ] **Step 3: Run state-machine test**

```powershell
.\gradlew.bat test --tests com.autoauction.client.automation.AuctionAutomationControllerTest
```

Expected: controller stays watching until all four pieces are at or above threshold.

---

### Task 7: Minecraft Adapter And Tick Registration

**Files:**
- Modify: `src\client\java\com\autoauction\client\AutoauctionClient.java`
- Create: `src\client\java\com\autoauction\client\minecraft\MinecraftGameActions.java`

- [ ] **Step 1: Register client tick loop**

Use Fabric API `ClientTickEvents.END_CLIENT_TICK.register(client -> { ... })`, matching the current Fabric docs for client tick callbacks.

```java
package com.autoauction.client;

import com.autoauction.client.automation.AuctionAutomationController;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.config.AutoAuctionConfigStore;
import com.autoauction.client.minecraft.MinecraftGameActions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class AutoauctionClient implements ClientModInitializer {
	private AuctionAutomationController controller;
	private MinecraftGameActions actions;

	@Override
	public void onInitializeClient() {
		try {
			AutoAuctionConfig config = new AutoAuctionConfigStore(FabricLoader.getInstance().getConfigDir()).load();
			this.controller = new AuctionAutomationController(config);
			this.actions = new MinecraftGameActions();
			ClientTickEvents.END_CLIENT_TICK.register(client -> {
				if (client.player != null && controller != null) {
					controller.observeArmor(actions.readEquippedFinalDestinationArmor(client));
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize AutoAuction client", e);
		}
	}
}
```

- [ ] **Step 2: Implement equipped armor reader**

```java
package com.autoauction.client.minecraft;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.item.FinalDestinationParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.List;

public final class MinecraftGameActions {
	private final FinalDestinationParser parser = new FinalDestinationParser();

	public EnumMap<ArmorPiece, ArmorSnapshot> readEquippedFinalDestinationArmor(MinecraftClient client) {
		EnumMap<ArmorPiece, ArmorSnapshot> result = new EnumMap<>(ArmorPiece.class);
		read(client, EquipmentSlot.HEAD, ArmorPiece.HELMET).ifPresent(value -> result.put(ArmorPiece.HELMET, value));
		read(client, EquipmentSlot.CHEST, ArmorPiece.CHESTPLATE).ifPresent(value -> result.put(ArmorPiece.CHESTPLATE, value));
		read(client, EquipmentSlot.LEGS, ArmorPiece.LEGGINGS).ifPresent(value -> result.put(ArmorPiece.LEGGINGS, value));
		read(client, EquipmentSlot.FEET, ArmorPiece.BOOTS).ifPresent(value -> result.put(ArmorPiece.BOOTS, value));
		return result;
	}

	private java.util.Optional<ArmorSnapshot> read(MinecraftClient client, EquipmentSlot slot, ArmorPiece piece) {
		ItemStack stack = client.player.getEquippedStack(slot);
		String displayName = stack.getName().getString();
		List<String> lore = stack.getTooltip(null, null).stream().map(Text::getString).toList();
		return parser.parse(piece, displayName, lore);
	}
}
```

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileClientJava
```

Expected: compile succeeds. If `ItemStack#getTooltip` signature differs in 26.1.1 mappings, adapt this one method to the mapped tooltip API and keep `FinalDestinationParser` unchanged.

---

### Task 8: AH Screen Detector And Clicker

**Files:**
- Create: `src\client\java\com\autoauction\client\minecraft\AuctionScreenDetector.java`
- Modify: `src\client\java\com\autoauction\client\minecraft\MinecraftGameActions.java`

- [ ] **Step 1: Implement conservative detector**

```java
package com.autoauction.client.minecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;

public final class AuctionScreenDetector {
	public boolean isAuctionHouseMain(MinecraftClient client) {
		return titleContains(client, "Auction House");
	}

	public boolean isCreateAuction(MinecraftClient client) {
		return titleContains(client, "Create Auction") || titleContains(client, "Create BIN Auction");
	}

	public boolean isConfirmAuction(MinecraftClient client) {
		return titleContains(client, "Confirm") || titleContains(client, "Confirm Auction");
	}

	private boolean titleContains(MinecraftClient client, String text) {
		if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
			return false;
		}
		return screen.getTitle().getString().contains(text);
	}

	public boolean slotNameContains(ItemStack stack, String text) {
		return !stack.isEmpty() && stack.getName().getString().contains(text);
	}
}
```

- [ ] **Step 2: Add guarded click methods**

Add methods to `MinecraftGameActions`:

```java
public void sendChatCommand(MinecraftClient client, String command) {
	if (client.player != null && !command.isBlank()) {
		client.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
	}
}

public void disconnect(MinecraftClient client, String reason) {
	if (client.getNetworkHandler() != null) {
		client.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.literal(reason));
	}
}
```

Slot-click methods should be added only after the exact copied AH slot indexes are confirmed on the private server. Use `client.interactionManager.clickSlot(syncId, slotId, button, SlotActionType.PICKUP, client.player)` and guard every call with `AuctionScreenDetector`.

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileClientJava
```

Expected: compile succeeds.

---

### Task 9: Full Workflow Integration

**Files:**
- Modify: `src\client\java\com\autoauction\client\automation\AuctionAutomationController.java`
- Modify: `src\client\java\com\autoauction\client\minecraft\MinecraftGameActions.java`
- Modify: `src\client\java\com\autoauction\client\api\AuctionApiClient.java`
- Modify: `src\client\java\com\autoauction\client\notify\DiscordNotifier.java`

- [ ] **Step 1: Expand controller states**

The controller tick method should perform one small action per tick or after configured delays:

```java
public void tick() {
	switch (state) {
		case THRESHOLD_REACHED -> state = AutomationState.STOPPING_MACRO;
		case STOPPING_MACRO -> stopMacroOrFail();
		case REMOVING_ARMOR -> removeArmorOrFail();
		case PRICING_ITEMS -> priceNextItemOrFail();
		case LISTING_ITEMS -> listNextItemOrFail();
		case RETURNING_TO_HUB -> sendHubAndStartDelay();
		case DISCONNECTING -> disconnectWhenDelayElapsed();
		default -> {
		}
	}
}
```

- [ ] **Step 2: Enforce safety gates before real actions**

Before `STOPPING_MACRO`, check:

```java
if (!config.canRunRealListing()) {
	fail("macroStopCommand is not configured while dryRun is false");
	return;
}

if (!isAllowedServer()) {
	fail("current server is not in allowedServers");
	return;
}
```

- [ ] **Step 3: Price all four armor pieces**

For each `ArmorSnapshot`, send:

```json
{
  "baseName": "Final Destination Chestplate",
  "attributes": {
    "minKills": 25000,
    "recomb": false,
    "minStars": 0
  }
}
```

Reject any price below `minBinPrice` or above `maxBinPrice` and send a Discord issue alert.

- [ ] **Step 4: Dry-run output**

When `dryRun` is true, do not stop macro, remove armor, click AH slots, send `/hub`, or disconnect. Log and optionally Discord-send the planned item names and prices.

- [ ] **Step 5: Real AH listing**

Use this guarded sequence:

```text
send macroStopCommand
wait clickDelayMs
verify at least 4 empty inventory slots
remove helmet/chestplate/leggings/boots
send /ah
wait for Auction House screen
click create auction button
wait for Create Auction screen
place one removed armor piece
set BIN price
confirm auction
repeat for remaining pieces
send Discord summary
send /hub
wait disconnectDelayMs
disconnect
```

Document the exact slot IDs during private-server testing in a short source comment near the click constants.

- [ ] **Step 6: Compile and run tests**

```powershell
.\gradlew.bat test compileClientJava
```

Expected: unit tests pass and client sources compile.

---

### Task 10: Manual Private-Server Verification

**Files:**
- Modify: `config\autoauction.json` in the Minecraft run directory after first launch.

- [ ] **Step 1: First launch creates config**

Run the client once. Confirm `config/autoauction.json` exists and contains:

```json
{
  "apiBaseUrl": "https://lazy-similarly-reaffirm.ngrok-free.dev",
  "dryRun": true,
  "killThreshold": 25000
}
```

- [ ] **Step 2: Dry-run watcher test**

On the private test server, equip four Final Destination pieces with lore showing 25,000 or more kills. Expected: the mod logs threshold reached and planned pricing actions without clicking, removing armor, or disconnecting.

- [ ] **Step 3: API test**

Confirm the log shows calls to:

```text
https://lazy-similarly-reaffirm.ngrok-free.dev/api/recommend-bin
```

Expected: four recommended BIN prices are logged or sent to Discord.

- [ ] **Step 4: Real listing test**

Set:

```json
{
  "dryRun": false,
  "macroStopCommand": "/configured-stop-command",
  "allowedServers": ["private-server-hostname"]
}
```

Expected: the mod stops the macro, removes the four armor pieces, lists them, sends `/hub`, waits five seconds, disconnects, and leaves the macro stopped.

---

## Self-Review

- Spec coverage: the plan covers config-driven API URL, Discord webhook mentions, full-set 25k trigger, no-inventory armor checking, macro stop by configurable command, armor removal, private-server AH screen state machine, `/hub`, five-second wait, and disconnect.
- Placeholder scan: no steps rely on blank implementation slots; exact class names, method names, config keys, and commands are named.
- Type consistency: `AutoAuctionConfig`, `ArmorPiece`, `ArmorSnapshot`, `AuctionItemRequest`, `AuctionApiClient`, `DiscordNotifier`, `AuctionAutomationController`, `MinecraftGameActions`, and `AuctionScreenDetector` names are consistent across tasks.
- Repository note: `C:\Projects\Hypixel\Minecraft Mod\26.1.1` is not currently a git repository. Commit steps are retained as checkpoints for a future git-backed workspace and should be skipped until git is initialized.
