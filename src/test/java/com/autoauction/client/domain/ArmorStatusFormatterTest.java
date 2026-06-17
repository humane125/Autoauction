package com.autoauction.client.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorStatusFormatterTest {
	@Test
	void formatsEquippedFinalDestinationKillsAndReadiness() {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		armor.put(ArmorPiece.HELMET, snapshot(ArmorPiece.HELMET, 25_432));
		armor.put(ArmorPiece.CHESTPLATE, snapshot(ArmorPiece.CHESTPLATE, 25_118));
		armor.put(ArmorPiece.LEGGINGS, snapshot(ArmorPiece.LEGGINGS, 24_980));
		armor.put(ArmorPiece.BOOTS, snapshot(ArmorPiece.BOOTS, 25_001));

		assertEquals("""
			AutoAuction armor:
			Helmet: 25,432 kills
			Chestplate: 25,118 kills
			Leggings: 24,980 kills
			Boots: 25,001 kills
			Threshold: 25,000
			Ready: no""".lines().toList(), ArmorStatusFormatter.format(armor, 25_000));
	}

	@Test
	void marksMissingPieces() {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		armor.put(ArmorPiece.HELMET, snapshot(ArmorPiece.HELMET, 25_432));

		assertEquals("Chestplate: missing", ArmorStatusFormatter.format(armor, 25_000).get(2));
		assertEquals("Ready: no", ArmorStatusFormatter.format(armor, 25_000).getLast());
	}

	private ArmorSnapshot snapshot(ArmorPiece piece, int kills) {
		return new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), kills, false, 0);
	}
}
