package com.autoauction.client.crafting;

import com.autoauction.client.domain.ArmorPiece;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FinalDestinationCraftingPlan {
	public static final String ENCHANTED_REDSTONE_DUST = "Enchanted Redstone Dust";
	public static final String NULL_OVOID = "Null Ovoid";
	public static final int ENCHANTED_REDSTONE_DUST_REQUIRED = 768;
	public static final int NULL_OVOID_REQUIRED = 12;
	public static final int OUTPUT_SLOT = 23;

	private static final Map<ArmorPiece, Recipe> RECIPES = createRecipes();

	private FinalDestinationCraftingPlan() {
	}

	public static Materials missingMaterials(int enchantedRedstoneDust, int nullOvoid) {
		return new Materials(
			Math.max(0, ENCHANTED_REDSTONE_DUST_REQUIRED - Math.max(0, enchantedRedstoneDust)),
			Math.max(0, NULL_OVOID_REQUIRED - Math.max(0, nullOvoid))
		);
	}

	public static Recipe recipe(ArmorPiece piece) {
		Recipe recipe = RECIPES.get(piece);
		if (recipe == null) {
			throw new IllegalArgumentException("Unsupported armor piece: " + piece);
		}
		return recipe;
	}

	public static List<ArmorPiece> craftOrder() {
		return List.of(ArmorPiece.HELMET, ArmorPiece.CHESTPLATE, ArmorPiece.LEGGINGS, ArmorPiece.BOOTS);
	}

	public static Map<ArmorPiece, Integer> redstoneStackCounts() {
		EnumMap<ArmorPiece, Integer> counts = new EnumMap<>(ArmorPiece.class);
		for (Map.Entry<ArmorPiece, Recipe> entry : RECIPES.entrySet()) {
			counts.put(entry.getKey(), entry.getValue().redstoneDust() / 64);
		}
		return counts;
	}

	public static Map<ArmorPiece, Integer> nullOvoidCounts() {
		EnumMap<ArmorPiece, Integer> counts = new EnumMap<>(ArmorPiece.class);
		for (Map.Entry<ArmorPiece, Recipe> entry : RECIPES.entrySet()) {
			counts.put(entry.getKey(), entry.getValue().nullOvoid());
		}
		return counts;
	}

	private static Map<ArmorPiece, Recipe> createRecipes() {
		EnumMap<ArmorPiece, Recipe> recipes = new EnumMap<>(ArmorPiece.class);
		recipes.put(ArmorPiece.HELMET, new Recipe(ArmorPiece.HELMET, List.of(
			new Placement(Ingredient.NULL_OVOID, 10, 1),
			new Placement(Ingredient.NULL_OVOID, 11, 1),
			new Placement(Ingredient.NULL_OVOID, 12, 1),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 19, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 21, 64)
		)));
		recipes.put(ArmorPiece.CHESTPLATE, new Recipe(ArmorPiece.CHESTPLATE, List.of(
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 10, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 12, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 19, 64),
			new Placement(Ingredient.NULL_OVOID, 20, 1),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 21, 64),
			new Placement(Ingredient.NULL_OVOID, 28, 1),
			new Placement(Ingredient.NULL_OVOID, 29, 1),
			new Placement(Ingredient.NULL_OVOID, 30, 1)
		)));
		recipes.put(ArmorPiece.LEGGINGS, new Recipe(ArmorPiece.LEGGINGS, List.of(
			new Placement(Ingredient.NULL_OVOID, 10, 1),
			new Placement(Ingredient.NULL_OVOID, 11, 1),
			new Placement(Ingredient.NULL_OVOID, 12, 1),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 19, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 21, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 28, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 30, 64)
		)));
		recipes.put(ArmorPiece.BOOTS, new Recipe(ArmorPiece.BOOTS, List.of(
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 19, 64),
			new Placement(Ingredient.ENCHANTED_REDSTONE_DUST, 21, 64),
			new Placement(Ingredient.NULL_OVOID, 28, 1),
			new Placement(Ingredient.NULL_OVOID, 30, 1)
		)));
		return Map.copyOf(recipes);
	}

	public enum Ingredient {
		ENCHANTED_REDSTONE_DUST(FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST),
		NULL_OVOID(FinalDestinationCraftingPlan.NULL_OVOID);

		private final String itemName;

		Ingredient(String itemName) {
			this.itemName = itemName;
		}

		public String itemName() {
			return itemName;
		}
	}

	public record Materials(int enchantedRedstoneDust, int nullOvoid) {
		public boolean complete() {
			return enchantedRedstoneDust <= 0 && nullOvoid <= 0;
		}
	}

	public record Placement(Ingredient ingredient, int slot, int count) {
	}

	public record Recipe(ArmorPiece piece, List<Placement> placements) {
		public int redstoneDust() {
			return placements.stream()
				.filter(placement -> placement.ingredient() == Ingredient.ENCHANTED_REDSTONE_DUST)
				.mapToInt(Placement::count)
				.sum();
		}

		public int nullOvoid() {
			return placements.stream()
				.filter(placement -> placement.ingredient() == Ingredient.NULL_OVOID)
				.mapToInt(Placement::count)
				.sum();
		}
	}
}
