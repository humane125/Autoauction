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

		assertEquals("https://lazy-similarly-reaffirm.ngrok-free.dev", config.apiBaseUrl());
		assertTrue(config.dryRun());
		assertFalse(config.enabledByDefault());
		assertEquals(25000, config.killThreshold());
		assertTrue(Files.exists(tempDir.resolve("autoauction.json")));
	}

	@Test
	void rejectsRealListingWithoutMacroStopCommand() {
		AutoAuctionConfig config = AutoAuctionConfig.defaults()
			.withDryRun(false)
			.withMacroStopCommand("");

		assertFalse(config.canRunRealListing());
	}
}
