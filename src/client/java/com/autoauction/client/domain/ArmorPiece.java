package com.autoauction.client.domain;

public enum ArmorPiece {
	HELMET("Final Destination Helmet"),
	CHESTPLATE("Final Destination Chestplate"),
	LEGGINGS("Final Destination Leggings"),
	BOOTS("Final Destination Boots");

	private final String baseName;

	ArmorPiece(String baseName) {
		this.baseName = baseName;
	}

	public String baseName() {
		return baseName;
	}
}
