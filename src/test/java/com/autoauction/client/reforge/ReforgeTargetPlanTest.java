package com.autoauction.client.reforge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReforgeTargetPlanTest {
	@Test
	void parsesFdArmorAndMatchesExistingReforgePrefixes() {
		ReforgeTargetPlan.Plan plan = ReforgeTargetPlan.parse("fd armor", "fierce").orElseThrow();

		assertEquals("fd armor", plan.name());
		assertEquals("Fierce", plan.reforge());
		assertTrue(ReforgeTargetPlan.itemMatchesPlan("Pure Final Destination Chestplate", plan));
		assertTrue(ReforgeTargetPlan.itemMatchesPlan("Final Destination Boots", plan));
		assertTrue(ReforgeTargetPlan.itemHasTargetReforge("Fierce Final Destination Helmet", "Fierce"));
		assertFalse(ReforgeTargetPlan.itemHasTargetReforge("Pure Final Destination Helmet", "Fierce"));
		assertFalse(ReforgeTargetPlan.itemMatchesPlan("Fierce Glacite Chestplate", plan));
	}

	@Test
	void validatesArmorAndSwordReforgeSetsSeparately() {
		assertTrue(ReforgeTargetPlan.parse("glacite armor", "wise").isPresent());
		assertFalse(ReforgeTargetPlan.parse("fd armor", "spicy").isPresent());
		assertTrue(ReforgeTargetPlan.parse("voidwalker", "spicy").isPresent());
		assertFalse(ReforgeTargetPlan.parse("voidwalker", "wise").isPresent());
	}

	@Test
	void voidwalkerMatchesKatanaNamesWithExistingPrefixes() {
		ReforgeTargetPlan.Plan plan = ReforgeTargetPlan.parse("voidwalker", "spicy").orElseThrow();

		assertTrue(ReforgeTargetPlan.itemMatchesPlan("Fabled Voidedge Katana", plan));
		assertTrue(ReforgeTargetPlan.itemMatchesPlan("Sharp Voidwalker Katana", plan));
		assertFalse(ReforgeTargetPlan.itemMatchesPlan("Aspect of the End", plan));
	}

	@Test
	void exposesLowercaseSuggestionsForCommandAutocomplete() {
		assertEquals(List.of("clean", "fierce", "heavy", "light", "mythic", "pure", "smart", "titanic", "wise"),
			ReforgeTargetPlan.armorReforgeSuggestions());
		assertEquals(List.of("fair", "epic", "fast", "gentle", "heroic", "legendary", "odd", "rich", "sharp", "spicy"),
			ReforgeTargetPlan.swordReforgeSuggestions());
	}
}
