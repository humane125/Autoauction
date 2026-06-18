package com.autoauction.client.transfer;

import java.util.Locale;

public final class BazaarProductId {
	private BazaarProductId() {
	}

	public static String fromItemName(String itemName) {
		String clean = String.valueOf(itemName == null ? "" : itemName)
			.replaceAll("(?i)§.", "")
			.replaceAll("[^A-Za-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		return clean.toUpperCase(Locale.ROOT);
	}
}
