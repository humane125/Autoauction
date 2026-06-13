package com.autoauction.client.domain;

public record ArmorSnapshot(
	ArmorPiece piece,
	String displayName,
	String baseName,
	int kills,
	boolean recomb,
	int stars
) {
	public boolean reached(int threshold) {
		return kills >= threshold;
	}
}
