package com.autoauction.client.stats;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummoningEyeEventDetector {
	private static final Pattern SOLD_PATTERN = Pattern.compile("(?i)\\bsold\\s+([0-9,]+)x\\s+summoning eye\\b");
	private static final Pattern SELL_ORDER_PATTERN = Pattern.compile("(?i)\\bsell order setup!\\s+([0-9,]+)x\\s+summoning eye\\s+at\\s+([0-9,]+)\\s+coins each\\b");
	private static final Pattern SELL_OFFER_PATTERN = Pattern.compile("(?i)\\bsell offer setup!\\s+([0-9,]+)x\\s+summoning eye\\s+for\\s+([0-9,]+)\\s+coins?\\b");
	private static final Pattern CLAIMED_PATTERN = Pattern.compile("(?i)\\bclaimed\\s+([0-9,]+)\\s+coins\\s+from\\s+selling\\s+([0-9,]+)x\\s+summoning eye\\s+at\\s+([0-9,]+)\\s+each\\b");
	private static final Pattern CANCELLED_PATTERN = Pattern.compile("(?i)\\bcancelled!\\s+refunded\\s+([0-9,]+)x\\s+summoning eye\\s+from\\s+cancelling\\s+sell offer\\b");

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
		Matcher sellOffer = SELL_OFFER_PATTERN.matcher(clean);
		if (sellOffer.find()) {
			int quantity = parseInt(sellOffer.group(1), 1);
			int totalCoins = parseInt(sellOffer.group(2), 0);
			return Optional.of(new SummoningEyeEvent("sell_order", quantity, pricePerItem(totalCoins, quantity)));
		}
		Matcher claimed = CLAIMED_PATTERN.matcher(clean);
		if (claimed.find()) {
			return Optional.of(new SummoningEyeEvent(
				"claimed",
				parseInt(claimed.group(2), 1),
				parseInt(claimed.group(3), 0)
			));
		}
		Matcher cancelled = CANCELLED_PATTERN.matcher(clean);
		if (cancelled.find()) {
			return Optional.of(new SummoningEyeEvent("cancelled", parseInt(cancelled.group(1), 1), 0));
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

	private static int pricePerItem(int totalCoins, int quantity) {
		if (totalCoins <= 0 || quantity <= 0) {
			return 0;
		}
		return Math.max(0, totalCoins / quantity);
	}
}
