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
	private static final String ARROW = "\u00c2\u00bb";
	private static final String COLOR_ARTIFACT = "\u00c3\u201a\u00c2\u00a7";

	@TempDir
	Path tempDir;

	@Test
	void matchesNebulaMacroLinesWithSpacingAndFormattingArtifacts() {
		assertTrue(NebulaLatestLogWatcher.isNebulaMacroLine("[Render thread/INFO]: [System] [CHAT] "
			+ COLOR_ARTIFACT + "bNebulaClient " + COLOR_ARTIFACT + "7" + ARROW + " "
			+ COLOR_ARTIFACT + "aCombat Macro: Enabled"));
		assertTrue(NebulaLatestLogWatcher.isNebulaMacroLine("[Client thread/INFO]: [CHAT] NebulaClient "
			+ ARROW + " CombatMacro: Disabled"));
		assertFalse(NebulaLatestLogWatcher.isNebulaMacroLine("[Render thread/INFO]: [CHAT] Welcome to Hypixel SkyBlock!"));
	}

	@Test
	void formatsDisplayMessageWithoutLogPrefix() {
		assertEquals(
			"NebulaClient " + ARROW + " Combat Macro: Enabled",
			NebulaLatestLogWatcher.displayMessage("[Render thread/INFO]: [System] [CHAT] NebulaClient "
				+ ARROW + " Combat Macro: Enabled")
		);
	}

	@Test
	void ignoresAutoAuctionDebugEchoesOfNebulaMacroMessages() {
		assertFalse(NebulaLatestLogWatcher.isNebulaMacroLine("[Render thread/INFO]: [System] [CHAT] "
			+ "[AutoAuction debug/nebula] NebulaClient > Combat Macro: Enabled"));
	}

	@Test
	void matchesNebulaBanwaveLinesWithSpacingAndFormattingArtifacts() {
		assertTrue(NebulaLatestLogWatcher.isNebulaBanwaveLine("[Render thread/INFO]: [System] [CHAT] "
			+ COLOR_ARTIFACT + "bNebulaClient " + COLOR_ARTIFACT + "7" + ARROW + " "
			+ COLOR_ARTIFACT + "cBan Wave detected"));
		assertTrue(NebulaLatestLogWatcher.isNebulaBanwaveLine("[Client thread/INFO]: [CHAT] NebulaClient "
			+ ARROW + " banwave active"));
		assertFalse(NebulaLatestLogWatcher.isNebulaBanwaveLine("[Render thread/INFO]: [CHAT] ban wave in random chat"));
		assertFalse(NebulaLatestLogWatcher.isNebulaBanwaveLine("[Render thread/INFO]: [CHAT] "
			+ "[AutoAuction debug/nebula] NebulaClient " + ARROW + " Ban Wave detected"));
	}

	@Test
	void pollsOnlyNewMatchingLogLines() throws Exception {
		Path logFile = tempDir.resolve("latest.log");
		Files.writeString(logFile, "[INFO]: boot\n", StandardCharsets.UTF_8);
		NebulaLatestLogWatcher watcher = new NebulaLatestLogWatcher(logFile);

		assertEquals(List.of(), watcher.pollMacroMessages());

		Files.writeString(logFile,
			"[INFO]: boot\n"
				+ "[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Combat Macro: Enabled\n"
				+ "[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Ban Wave detected\n"
				+ "[Render thread/INFO]: [CHAT] normal chat\n",
			StandardCharsets.UTF_8);

		assertEquals(
			List.of(
				"[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Combat Macro: Enabled",
				"[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Ban Wave detected"
			),
			watcher.pollMacroMessages()
		);
		assertEquals(List.of(), watcher.pollMacroMessages());
	}

	@Test
	void resetsOffsetWhenLatestLogIsTruncated() throws Exception {
		Path logFile = tempDir.resolve("latest.log");
		Files.writeString(logFile,
			"[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Combat Macro: Enabled\n",
			StandardCharsets.UTF_8);
		NebulaLatestLogWatcher watcher = new NebulaLatestLogWatcher(logFile);

		assertEquals(1, watcher.pollMacroMessages().size());

		Files.writeString(logFile,
			"[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Combat Macro: Disabled\n",
			StandardCharsets.UTF_8);

		assertEquals(
			List.of("[Render thread/INFO]: [System] [CHAT] NebulaClient " + ARROW + " Combat Macro: Disabled"),
			watcher.pollMacroMessages()
		);
	}
}
