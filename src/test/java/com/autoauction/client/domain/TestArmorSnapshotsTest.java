package com.autoauction.client.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestArmorSnapshotsTest {
	@Test
	void createsFourFinalDestinationPiecesAtRequestedKills() {
		var armor = TestArmorSnapshots.finalDestinationSet(25_000);

		assertEquals(4, armor.size());
		for (ArmorPiece piece : ArmorPiece.values()) {
			ArmorSnapshot snapshot = armor.get(piece);
			assertEquals(piece.baseName(), snapshot.baseName());
			assertEquals(25_000, snapshot.kills());
			assertFalse(snapshot.recomb());
			assertEquals(0, snapshot.stars());
		}
	}
}
