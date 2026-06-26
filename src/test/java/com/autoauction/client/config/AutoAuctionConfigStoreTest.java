package com.autoauction.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoAuctionConfigStoreTest {
	@TempDir
	Path tempDir;

	@Test
	void createsDefaultConfigWhenMissing() throws Exception {
		AutoAuctionConfigStore store = new AutoAuctionConfigStore(tempDir);

		AutoAuctionConfig config = store.load();
		String json = Files.readString(tempDir.resolve("autoauction.json"));

		assertEquals("https://lazy-similarly-reaffirm.ngrok-free.dev", config.apiBaseUrl());
		assertTrue(config.enabledByDefault());
		assertEquals("", config.macroStartCommand());
		assertEquals("P", config.nebulaMacroToggleKey());
		assertEquals(25000, config.killThreshold());
		assertTrue(Files.exists(tempDir.resolve("autoauction.json")));
		assertTrue(json.contains("\"macroStartCommand\""));
		assertTrue(json.contains("\"nebulaMacroToggleKey\""));
		assertFalse(json.contains("dryRun"));
		assertFalse(json.contains("privateServerOnly"));
		assertFalse(json.contains("allowedServers"));
		assertFalse(json.contains("minBinPrice"));
		assertFalse(json.contains("maxBinPrice"));
	}

	@Test
	void realListingDoesNotRequireConfiguredMacroStopCommand() {
		AutoAuctionConfig config = AutoAuctionConfig.defaults()
			.withMacroStopCommand("");

		assertTrue(config.canRunRealListing());
	}

	@Test
	void rewritesExistingConfigWithoutRemovedFields() throws Exception {
		Files.writeString(tempDir.resolve("autoauction.json"), """
			{
			  "apiBaseUrl": "https://api.example",
			  "apiToken": "token",
			  "discordWebhookUrl": "https://discord.example",
			  "discordMentionUserId": "123",
			  "macroStopCommand": "/stopmacro",
			  "returnToHubCommand": "/hub",
			  "enabledByDefault": true,
			  "dryRun": true,
			  "privateServerOnly": false,
			  "allowedServers": ["localhost"],
			  "killThreshold": 25000,
			  "minBinPrice": 1000000,
			  "maxBinPrice": 30000000,
			  "screenTimeoutMs": 8000,
			  "clickDelayMs": 750,
			  "disconnectDelayMs": 5000
			}
			""");
		AutoAuctionConfigStore store = new AutoAuctionConfigStore(tempDir);

		AutoAuctionConfig config = store.load();
		String json = Files.readString(tempDir.resolve("autoauction.json"));

		assertEquals("https://api.example", config.apiBaseUrl());
		assertEquals("token", config.apiToken());
		assertEquals("", config.macroStartCommand());
		assertEquals("P", config.nebulaMacroToggleKey());
		assertTrue(config.enabledByDefault());
		assertTrue(json.contains("\"macroStartCommand\""));
		assertTrue(json.contains("\"nebulaMacroToggleKey\""));
		assertFalse(json.contains("dryRun"));
		assertFalse(json.contains("privateServerOnly"));
		assertFalse(json.contains("allowedServers"));
		assertFalse(json.contains("minBinPrice"));
		assertFalse(json.contains("maxBinPrice"));
	}
}
