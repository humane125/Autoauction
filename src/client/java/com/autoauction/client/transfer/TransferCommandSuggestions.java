package com.autoauction.client.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TransferCommandSuggestions {
	private static final Set<String> RESERVED_TRANSFER_WORDS = Set.of(
		"list",
		"accept",
		"decline",
		"run",
		"cancel"
	);

	private static final List<String> DEFAULT_ITEM_NAMES = List.of(
		"Magma Cream Distillate",
		"Blaze Rod Distillate",
		"Glowstone Distillate",
		"Nether Wart Distillate",
		"Gunpowder Distillate",
		"Gabagool Distillate",
		"Crude Gabagool Distillate"
	);

	private TransferCommandSuggestions() {
	}

	public static List<String> receiverUsernames(Collection<String> connectedUsernames) {
		if (connectedUsernames == null || connectedUsernames.isEmpty()) {
			return List.of();
		}
		List<String> suggestions = new ArrayList<>();
		for (String username : connectedUsernames) {
			String clean = String.valueOf(username == null ? "" : username).trim();
			if (clean.isBlank() || isReservedTransferWord(clean) || suggestions.stream().anyMatch(clean::equalsIgnoreCase)) {
				continue;
			}
			suggestions.add(clean);
		}
		return suggestions;
	}

	public static List<String> itemNames() {
		return DEFAULT_ITEM_NAMES;
	}

	public static boolean isReservedTransferWord(String value) {
		return RESERVED_TRANSFER_WORDS.contains(String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT));
	}
}
