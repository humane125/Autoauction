package com.autoauction.client.crafting;

import com.autoauction.client.domain.ArmorPiece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalDestinationCraftWorkflowTest {
	@Test
	void reusesCarriedNullOvoidStackForConsecutiveOneItemPlacements() {
		FinalDestinationCraftingPlan.Recipe helmet = FinalDestinationCraftingPlan.recipe(ArmorPiece.HELMET);
		FinalDestinationCraftingPlan.Recipe chestplate = FinalDestinationCraftingPlan.recipe(ArmorPiece.CHESTPLATE);

		assertTrue(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(helmet, 0));
		assertTrue(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(helmet, 1));
		assertFalse(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(helmet, 2));

		assertFalse(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(chestplate, 3));
		assertTrue(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(chestplate, 5));
		assertTrue(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(chestplate, 6));
		assertFalse(FinalDestinationCraftWorkflow.canReuseCarriedForNextPlacement(chestplate, 7));
	}
}
