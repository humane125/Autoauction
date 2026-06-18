package com.autoauction.client.transfer;

import java.text.NumberFormat;
import java.util.Locale;

public final class TransferDebugMessages {
	private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

	private TransferDebugMessages() {
	}

	public static String step(String role, String phase, int quantity, String itemName, String action) {
		return prefix(role, phase, quantity, itemName) + " :: " + clean(action, "step");
	}

	public static String state(String role, String phase, String state, int quantity, String itemName, int nextDelayMs) {
		return "AutoAuction transfer [" + clean(role, "unknown") + "/" + clean(phase, "unknown") + "] state=" + clean(state, "unknown")
			+ " qty=" + Math.max(1, quantity)
			+ " item=" + clean(itemName, "unknown")
			+ " nextDelayMs=" + Math.max(0, nextDelayMs);
	}

	public static String purseWait(String role, String phase, long before, long current, long delta) {
		return "AutoAuction transfer [" + clean(role, "unknown") + "/" + clean(phase, "unknown") + "] waiting for purse update: before="
			+ formatCoins(before)
			+ " current=" + formatCoins(current)
			+ " delta=" + formatSignedCoins(delta);
	}

	private static String prefix(String role, String phase, int quantity, String itemName) {
		return "AutoAuction transfer [" + clean(role, "unknown") + "/" + clean(phase, "unknown") + "] qty="
			+ Math.max(1, quantity)
			+ " item=" + clean(itemName, "unknown");
	}

	private static String clean(String value, String fallback) {
		String clean = String.valueOf(value == null ? "" : value).trim();
		return clean.isBlank() ? fallback : clean;
	}

	private static String formatCoins(long coins) {
		return INTEGER_FORMAT.format(coins);
	}

	private static String formatSignedCoins(long coins) {
		return (coins >= 0 ? "+" : "") + formatCoins(coins);
	}
}
