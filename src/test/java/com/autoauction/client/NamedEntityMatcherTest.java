package com.autoauction.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedEntityMatcherTest {
	@Test
	void matchesColorFormattedArmorStandName() {
		assertTrue(NamedEntityMatcher.matchesName("\u00a7e\u00a7lBlacksmith", "blacksmith"));
	}

	@Test
	void matchesWhenRankOrDecorationSurroundsName() {
		assertTrue(NamedEntityMatcher.matchesName("\u00a76[NPC] Blacksmith", "blacksmith"));
	}

	@Test
	void rejectsBlankNames() {
		assertFalse(NamedEntityMatcher.matchesName("", "blacksmith"));
		assertFalse(NamedEntityMatcher.matchesName("Blacksmith", ""));
	}
}
