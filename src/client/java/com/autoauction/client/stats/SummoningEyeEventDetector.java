package com.autoauction.client.stats;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummoningEyeEventDetector {
	private static final Pattern SOLD_PATTERN = Pattern.compile("(?i)\\bsold\\s+([0-9,]+)x\\s+summoning eye\\b");
	private static final Pattern SELL_ORDER_PATTERN = Pattern.compile("(?i)\\bsell order setup!\\s+([0-9,]+)x\\s+summoning eye\\s+at\\s+([0-9,]+)\\s+coins each\\b");

	private SummoningEyeEventDetector() {
	}

	public static Optional<SummoningEyeEvent> detect(String message) {
		String clean = clean(message);
		String lower = clean.toLowerCase(Locale.ROOT);
		if (lower.contains("rare drop!") && lower.contains("summoning eye")) {
			return Optional.of(new SummoningEyeEvent("drop", 1, 0));
		}
		Matcher sellOrder = SELL_ORDER_PATTERN.matcher(clean);
		if (sellOrder.find()) {
			return Optional.of(new SummoningEyeEvent(
				"sell_order",
				parseInt(sellOrder.group(1), 1),
				parseInt(sellOrder.group(2), 0)
			));
		}
		Matcher sold = SOLD_PATTERN.matcher(clean);
		if (sold.find()) {
			return Optional.of(new SummoningEyeEvent("instant_sell", parseInt(sold.group(1), 1), 0));
		}
		return Optional.empty();
	}

	private static String clean(String message) {
		return String.valueOf(message == null ? "" : message)
			.replaceAll("(?i)(?:\u00C2?\u00A7|&)[0-9A-FK-OR]", "")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Math.max(0, Integer.parseInt(String.valueOf(value == null ? "" : value).replace(",", "")));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}
}
