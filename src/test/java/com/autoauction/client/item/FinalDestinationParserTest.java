package com.autoauction.client.item;

import com.autoauction.client.domain.ArmorPiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalDestinationParserTest {
	private final FinalDestinationParser parser = new FinalDestinationParser();

	@Test
	void parsesFinalDestinationKillsWithCommas() {
		var result = parser.parse(ArmorPiece.CHESTPLATE, "Ancient Final Destination Chestplate",
			List.of("Kills: 25,437", "LEGENDARY CHESTPLATE"));

		assertTrue(result.isPresent());
		assertEquals(25_437, result.get().kills());
		assertEquals("Final Destination Chestplate", result.get().baseName());
	}

	@Test
	void parsesFinalDestinationKillsWithLegacyFormattingCodes() {
		var result = parser.parse(ArmorPiece.LEGGINGS, "\u00a76Ancient Final Destination Leggings",
			List.of("\u00a77Kills: \u00a7c25,437", "\u00a76LEGENDARY LEGGINGS"));

		assertTrue(result.isPresent());
		assertEquals(25_437, result.get().kills());
	}

	@Test
	void parsesFinalDestinationKillsFromNextUpgradeProgress() {
		var result = parser.parse(ArmorPiece.CHESTPLATE, "\u00a76Fierce Final Destination Chestplate",
			List.of(
				"\u00a76Piece Bonus: Enderman Bulwark",
				"\u00a77Kill Endermen to accumulate defense",
				"\u00a77against them.",
				"\u00a76Piece Bonus: \u00a7a+310\u2726",
				"\u00a77Next Upgrade: \u00a7a+395\u2726 \u00a7e(24,393/25,000)",
				"\u00a76LEGENDARY CHESTPLATE"));

		assertTrue(result.isPresent());
		assertEquals(24_393, result.get().kills());
	}

	@Test
	void ignoresNonFinalDestinationArmor() {
		var result = parser.parse(ArmorPiece.BOOTS, "Ancient Necron's Boots", List.of("Kills: 25,437"));

		assertTrue(result.isEmpty());
	}
}
