package com.autoauction.client.domain;

import java.util.EnumMap;

public final class TestArmorSnapshots {
	private TestArmorSnapshots() {
	}

	public static EnumMap<ArmorPiece, ArmorSnapshot> finalDestinationSet(int kills) {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		for (ArmorPiece piece : ArmorPiece.values()) {
			armor.put(piece, new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), kills, false, 0));
		}
		return armor;
	}
}
