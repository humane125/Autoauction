package com.autoauction.client.debug;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyBlockStatusTest {
	@Test
	void readsAreaFromTabListAndMapsKnownIslands() {
		SkyBlockStatus status = SkyBlockStatus.fromLines(
			List.of("title=SKYBLOCK", "Your Island", "Purse: 1,875,543"),
			List.of("Area: Hub", "Profile: Apple")
		);

		assertEquals("Hub", status.area().orElseThrow());
		assertEquals(SkyBlockStatus.Island.HUB, status.currentIsland().orElseThrow());
		assertEquals("Hub", status.currentIsland().orElseThrow().displayName());
		assertEquals(1_875_543L, status.purse().orElseThrow());
	}

	@Test
	void treatsPrivateIslandAsPrivateIslandFromTabListOnly() {
		SkyBlockStatus tabStatus = SkyBlockStatus.fromLines(List.of(), List.of("Area: Private Island"));
		SkyBlockStatus scoreboardStatus = SkyBlockStatus.fromLines(List.of("Your Island", "Purse: 44,000"), List.of());

		assertEquals(SkyBlockStatus.Island.PRIVATE_ISLAND, tabStatus.currentIsland().orElseThrow());
		assertEquals("Private Island", tabStatus.currentIsland().orElseThrow().displayName());
		assertTrue(scoreboardStatus.currentIsland().isEmpty());
		assertEquals(44_000L, scoreboardStatus.purse().orElseThrow());
	}

	@Test
	void stripsMinecraftColorAndControlSuffixesFromStatusLines() {
		SkyBlockStatus status = SkyBlockStatus.fromLines(
			List.of("Purse: \u00a7613,132,\u00a7s\u00a76921", "\u00a77\u23e3 \u00a7bVillage\u00a7u"),
			List.of(" \u00a7aArea: Hub")
		);

		assertEquals("Hub", status.area().orElseThrow());
		assertEquals(13_132_921L, status.purse().orElseThrow());
	}

	@Test
	void returnsEmptyWhenStatusIsMissing() {
		SkyBlockStatus status = SkyBlockStatus.fromLines(List.of("Objective"), List.of("Players (2)"));

		assertTrue(status.area().isEmpty());
		assertTrue(status.currentIsland().isEmpty());
		assertTrue(status.purse().isEmpty());
	}

	@Test
	void detectsSkyBlockFromScoreboardTitle() {
		assertTrue(SkyBlockStatus.hasSkyBlockScoreboard(List.of("title=SKYBLOCK", "Purse: 32,444,946")));
		assertTrue(SkyBlockStatus.hasSkyBlockScoreboard(List.of("SKYBLOCK", "Purse: 32,444,946")));
		assertFalse(SkyBlockStatus.hasSkyBlockScoreboard(List.of("title=HYPIXEL", "Lobby")));
		assertFalse(SkyBlockStatus.hasSkyBlockScoreboard(List.of()));
	}
}
