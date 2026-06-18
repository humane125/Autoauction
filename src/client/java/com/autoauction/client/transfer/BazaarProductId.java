package com.autoauction.client.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BazaarProductId {
	private static final Map<String, String> ALIASES = Map.of(
		"GLOWSTONE_DISTILLATE", "GLOWSTONE_DUST_DISTILLATE",
		"NETHER_WART_DISTILLATE", "NETHER_STALK_DISTILLATE",
		"NETHER_WARTS_DISTILLATE", "NETHER_STALK_DISTILLATE"
	);

	private BazaarProductId() {
	}

	public static String fromItemName(String itemName) {
		String clean = String.valueOf(itemName == null ? "" : itemName)
			.replaceAll("(?i)§.", "")
			.replaceAll("[^A-Za-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		return clean.toUpperCase(Locale.ROOT);
	}

	public static String resolveProductId(String itemNameOrProductId, Collection<String> productIds) {
		String requested = fromItemName(itemNameOrProductId);
		if (requested.isBlank() || productIds == null || productIds.isEmpty()) {
			return requested;
		}

		String exact = findProductId(requested, productIds);
		if (!exact.isBlank()) {
			return exact;
		}

		String alias = ALIASES.get(requested);
		if (alias != null) {
			String aliased = findProductId(alias, productIds);
			if (!aliased.isBlank()) {
				return aliased;
			}
		}

		List<String> requestedTokens = tokens(requested);
		List<String> matches = new ArrayList<>();
		for (String productId : productIds) {
			if (containsOrderedTokens(tokens(productId), requestedTokens)) {
				matches.add(productId);
			}
		}
		return matches.size() == 1 ? matches.get(0) : requested;
	}

	private static String findProductId(String requested, Collection<String> productIds) {
		for (String productId : productIds) {
			if (fromItemName(productId).equals(requested)) {
				return productId;
			}
		}
		return "";
	}

	private static List<String> tokens(String productId) {
		String clean = fromItemName(productId);
		if (clean.isBlank()) {
			return List.of();
		}
		return List.of(clean.split("_"));
	}

	private static boolean containsOrderedTokens(List<String> candidateTokens, List<String> requestedTokens) {
		if (requestedTokens.isEmpty() || candidateTokens.isEmpty()) {
			return false;
		}
		int index = 0;
		for (String candidateToken : candidateTokens) {
			if (candidateToken.equals(requestedTokens.get(index))) {
				index++;
				if (index == requestedTokens.size()) {
					return true;
				}
			}
		}
		return false;
	}
}
