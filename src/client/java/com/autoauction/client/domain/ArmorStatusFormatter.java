package com.autoauction.client.domain;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArmorStatusFormatter {
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

	private ArmorStatusFormatter() {
	}

	public static List<String> format(Map<ArmorPiece, ArmorSnapshot> armor, int threshold) {
		List<String> lines = new ArrayList<>();
		lines.add("AutoAuction armor:");
		boolean ready = armor.size() == ArmorPiece.values().length;
		for (ArmorPiece piece : ArmorPiece.values()) {
			ArmorSnapshot snapshot = armor.get(piece);
			if (snapshot == null) {
				ready = false;
				lines.add(label(piece) + ": missing");
				continue;
			}
			if (!snapshot.reached(threshold)) {
				ready = false;
			}
			lines.add(label(piece) + ": " + NUMBER_FORMAT.format(snapshot.kills()) + " kills");
		}
		lines.add("Threshold: " + NUMBER_FORMAT.format(threshold));
		lines.add("Ready: " + (ready ? "yes" : "no"));
		return lines;
	}

	private static String label(ArmorPiece piece) {
		String name = piece.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
