package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NebulaLatestLogWatcherTest {
	@TempDir
	Path tempDir;

	@Test
	void matchesNebulaMacroLinesWithSpacingAndFormattingArtifacts() {
		assertTrue(NebulaLatestLogWatcher.isNebulaMacroLine("[Render thread/INFO]: [System] [CHAT] Â§bNebulaClient Â§7Â» Â§aCombat Macro: Enabled"));
		assertTrue(NebulaLatestLogWatcher.isNebulaMacroLine("[Client thread/INFO]: [CHAT] NebulaClient » CombatMacro: Disabled"));
		assertFalse(NebulaLatestLogWatcher.isNebulaMacroLine("[Render thread/INFO]: [CHAT] Welcome to Hypixel SkyBlock!"));
	}

	@Test
	void formatsDisplayMessageWithoutLogPrefix() {
		assertEquals(
			"NebulaClient » Combat Macro: Enabled",
			NebulaLatestLogWatcher.displayMessage("[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Enabled")
		);
	}

	@Test
	void pollsOnlyNewMatchingLogLines() throws Exception {
		Path logFile = tempDir.resolve("latest.log");
		Files.writeString(logFile, "[INFO]: boot\n", StandardCharsets.UTF_8);
		NebulaLatestLogWatcher watcher = new NebulaLatestLogWatcher(logFile);

		assertEquals(List.of(), watcher.pollMacroMessages());

		Files.writeString(logFile,
			"[INFO]: boot\n"
				+ "[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Enabled\n"
				+ "[Render thread/INFO]: [CHAT] normal chat\n",
			StandardCharsets.UTF_8);

		assertEquals(
			List.of("[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Enabled"),
			watcher.pollMacroMessages()
		);
		assertEquals(List.of(), watcher.pollMacroMessages());
	}

	@Test
	void resetsOffsetWhenLatestLogIsTruncated() throws Exception {
		Path logFile = tempDir.resolve("latest.log");
		Files.writeString(logFile,
			"[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Enabled\n",
			StandardCharsets.UTF_8);
		NebulaLatestLogWatcher watcher = new NebulaLatestLogWatcher(logFile);

		assertEquals(1, watcher.pollMacroMessages().size());

		Files.writeString(logFile,
			"[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Disabled\n",
			StandardCharsets.UTF_8);

		assertEquals(
			List.of("[Render thread/INFO]: [System] [CHAT] NebulaClient » Combat Macro: Disabled"),
			watcher.pollMacroMessages()
		);
	}
}
