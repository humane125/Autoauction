package com.autoauction.client.reforge;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ReforgeTargetPlan {
	public static final int INPUT_SLOT = 13;
	public static final int REFORGE_BUTTON_SLOT = 22;
	public static final int MOVE_DELAY_MS = 150;

	private static final List<String> ARMOR_REFORGES = List.of(
		"Clean", "Fierce", "Heavy", "Light", "Mythic", "Pure", "Smart", "Titanic", "Wise"
	);
	private static final List<String> SWORD_REFORGES = List.of(
		"Fair", "Epic", "Fast", "Gentle", "Heroic", "Legendary", "Odd", "Rich", "Sharp", "Spicy"
	);

	private ReforgeTargetPlan() {
	}

	public static Optional<Plan> parse(String target, String reforge) {
		String normalizedTarget = normalizeWords(target);
		if (normalizedTarget.equals("fdarmor") || normalizedTarget.equals("finaldestinationarmor")) {
			return armorPlan("fd armor", reforge, List.of(
				"Final Destination Helmet",
				"Final Destination Chestplate",
				"Final Destination Leggings",
				"Final Destination Boots"
			));
		}
		if (normalizedTarget.equals("glacitearmor")) {
			return armorPlan("glacite armor", reforge, List.of(
				"Glacite Helmet",
				"Glacite Chestplate",
				"Glacite Leggings",
				"Glacite Boots"
			));
		}
		if (normalizedTarget.equals("voidwalker") || normalizedTarget.equals("voidwalkersword")) {
			String canonical = canonicalReforge(reforge, SWORD_REFORGES);
			if (canonical.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new Plan("voidwalker", ItemKind.SWORD, canonical, List.of(
				"Voidwalker Katana",
				"Voidedge Katana"
			)));
		}
		return Optional.empty();
	}

	public static boolean itemMatchesPlan(String itemName, Plan plan) {
		String comparable = comparable(itemName);
		return plan.baseNames().stream().anyMatch(baseName -> comparable.contains(comparable(baseName)));
	}

	public static boolean itemHasTargetReforge(String itemName, String reforge) {
		return comparable(itemName).startsWith(comparable(reforge));
	}

	public static String displayTargets() {
		return "fd armor, glacite armor, voidwalker";
	}

	public static String displayArmorReforges() {
		return String.join(", ", ARMOR_REFORGES);
	}

	public static String displaySwordReforges() {
		return String.join(", ", SWORD_REFORGES);
	}

	private static Optional<Plan> armorPlan(String name, String reforge, List<String> baseNames) {
		String canonical = canonicalReforge(reforge, ARMOR_REFORGES);
		if (canonical.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Plan(name, ItemKind.ARMOR, canonical, baseNames));
	}

	private static String canonicalReforge(String requested, List<String> allowed) {
		String comparableRequested = comparable(requested);
		for (String candidate : allowed) {
			if (comparable(candidate).equals(comparableRequested)) {
				return candidate;
			}
		}
		return "";
	}

	private static String normalizeWords(String value) {
		return comparable(value);
	}

	private static String comparable(String value) {
		return String.valueOf(value == null ? "" : value)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]", "");
	}

	public enum ItemKind {
		ARMOR,
		SWORD
	}

	public record Plan(String name, ItemKind kind, String reforge, List<String> baseNames) {
	}
}
