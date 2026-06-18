package com.autoauction.client.debug;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyBlockIslandGuardTest {
	@Test
	void requiresWarpWhenTabAreaIsNotPrivateIsland() {
		assertTrue(SkyBlockIslandGuard.needsPrivateIslandWarp(status("Hub")));
		assertTrue(SkyBlockIslandGuard.needsPrivateIslandWarp(status("Dungeon Hub")));
		assertTrue(SkyBlockIslandGuard.needsPrivateIslandWarp(SkyBlockStatus.fromLines(List.of(), List.of())));
	}

	@Test
	void skipsWarpWhenTabAreaIsPrivateIsland() {
		assertFalse(SkyBlockIslandGuard.needsPrivateIslandWarp(status("Private Island")));
	}

	private SkyBlockStatus status(String area) {
		return SkyBlockStatus.fromLines(List.of(), List.of("Area: " + area));
	}
}
