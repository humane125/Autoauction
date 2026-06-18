package com.autoauction.client.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

public final class CoinAmountParser {
	private CoinAmountParser() {
	}

	public static OptionalLong parse(String value) {
		String clean = String.valueOf(value == null ? "" : value)
			.trim()
			.replace(",", "")
			.toLowerCase(Locale.ROOT);
		if (clean.isBlank()) {
			return OptionalLong.empty();
		}

		BigDecimal multiplier = BigDecimal.ONE;
		char suffix = clean.charAt(clean.length() - 1);
		if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
			clean = clean.substring(0, clean.length() - 1);
			multiplier = switch (suffix) {
				case 'k' -> BigDecimal.valueOf(1_000L);
				case 'm' -> BigDecimal.valueOf(1_000_000L);
				case 'b' -> BigDecimal.valueOf(1_000_000_000L);
				default -> BigDecimal.ONE;
			};
		}

		try {
			long coins = new BigDecimal(clean).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
			return coins > 0 ? OptionalLong.of(coins) : OptionalLong.empty();
		} catch (RuntimeException ignored) {
			return OptionalLong.empty();
		}
	}
}
