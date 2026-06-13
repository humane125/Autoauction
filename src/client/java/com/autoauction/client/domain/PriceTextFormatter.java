package com.autoauction.client.domain;

public final class PriceTextFormatter {
	private PriceTextFormatter() {
	}

	public static String forSign(int price) {
		if (price >= 1_000_000 && price % 1_000_000 == 0) {
			return (price / 1_000_000) + "m";
		}
		if (price >= 1_000_000 && price % 100_000 == 0) {
			return trimDecimal(price / 1_000_000.0) + "m";
		}
		if (price >= 1_000 && price % 1_000 == 0) {
			return (price / 1_000) + "k";
		}
		return Integer.toString(price);
	}

	private static String trimDecimal(double value) {
		String text = Double.toString(value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}
}
