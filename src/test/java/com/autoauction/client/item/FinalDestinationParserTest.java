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
	void ignoresNonFinalDestinationArmor() {
		var result = parser.parse(ArmorPiece.BOOTS, "Ancient Necron's Boots", List.of("Kills: 25,437"));

		assertTrue(result.isEmpty());
	}
}
