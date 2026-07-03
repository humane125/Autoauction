package com.autoauction.client.crafting;

import com.autoauction.client.domain.ArmorPiece;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinalDestinationCraftingPlanTest {
	@Test
	void fullSetNeedsExpectedMaterials() {
		assertEquals(768, FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST_REQUIRED);
		assertEquals(12, FinalDestinationCraftingPlan.NULL_OVOID_REQUIRED);
	}

	@Test
	void missingMaterialsOnlyBuysDeficit() {
		FinalDestinationCraftingPlan.Materials missing = FinalDestinationCraftingPlan.missingMaterials(700, 9);

		assertEquals(68, missing.enchantedRedstoneDust());
		assertEquals(3, missing.nullOvoid());
	}

	@Test
	void redstoneStacksAndOvoidClicksMatchRecipeImage() {
		assertEquals(Map.of(
			ArmorPiece.HELMET, 2,
			ArmorPiece.CHESTPLATE, 4,
			ArmorPiece.LEGGINGS, 4,
			ArmorPiece.BOOTS, 2
		), FinalDestinationCraftingPlan.redstoneStackCounts());
		assertEquals(Map.of(
			ArmorPiece.HELMET, 3,
			ArmorPiece.CHESTPLATE, 4,
			ArmorPiece.LEGGINGS, 3,
			ArmorPiece.BOOTS, 2
		), FinalDestinationCraftingPlan.nullOvoidCounts());
	}

	@Test
	void helmetPlacesOvoidsAndRedstoneIntoCraftGridSlots() {
		List<FinalDestinationCraftingPlan.Placement> placements = FinalDestinationCraftingPlan.recipe(ArmorPiece.HELMET).placements();

		assertEquals(List.of(
			new FinalDestinationCraftingPlan.Placement(FinalDestinationCraftingPlan.Ingredient.NULL_OVOID, 10, 1),
			new FinalDestinationCraftingPlan.Placement(FinalDestinationCraftingPlan.Ingredient.NULL_OVOID, 11, 1),
			new FinalDestinationCraftingPlan.Placement(FinalDestinationCraftingPlan.Ingredient.NULL_OVOID, 12, 1),
			new FinalDestinationCraftingPlan.Placement(FinalDestinationCraftingPlan.Ingredient.ENCHANTED_REDSTONE_DUST, 19, 64),
			new FinalDestinationCraftingPlan.Placement(FinalDestinationCraftingPlan.Ingredient.ENCHANTED_REDSTONE_DUST, 21, 64)
		), placements);
	}

	@Test
	void outputSlotMatchesCraftItemDump() {
		assertEquals(23, FinalDestinationCraftingPlan.OUTPUT_SLOT);
	}
}
