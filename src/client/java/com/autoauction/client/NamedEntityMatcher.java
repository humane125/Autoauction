package com.autoauction.client;

final class NamedEntityMatcher {
	private NamedEntityMatcher() {
	}

	static boolean matchesName(String actualName, String expectedName) {
		String actual = normalizeName(actualName);
		String expected = normalizeName(expectedName);
		return !actual.isBlank() && !expected.isBlank() && actual.contains(expected);
	}

	static String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		return name
			.replaceAll("(?i)\u00a7[0-9A-FK-OR]", "")
			.toLowerCase(java.util.Locale.ROOT)
			.trim();
	}
}
