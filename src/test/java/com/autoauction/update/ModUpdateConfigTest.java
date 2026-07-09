package com.autoauction.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModUpdateConfigTest {
	@TempDir
	Path tempDir;

	@Test
	void readsUpdaterFieldsFromAutoAuctionConfig() throws Exception {
		Files.writeString(tempDir.resolve("autoauction.json"), """
			{
			  "apiBaseUrl": " https://example.test/ ",
			  "apiToken": " token-value ",
			  "unrelated": "kept by client config"
			}
			""");

		ModUpdateConfig config = ModUpdateConfig.load(tempDir);

		assertEquals("https://example.test/", config.apiBaseUrl());
		assertEquals("token-value", config.apiToken());
	}
}
