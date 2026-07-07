package com.autoauction.client.crafting;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.minecraft.MinecraftGameActions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void failureCallbackDoesNotRunSuccessHandler() throws Exception {
		AtomicInteger failures = new AtomicInteger();
		AtomicInteger successes = new AtomicInteger();
		FinalDestinationCraftWorkflow workflow = new FinalDestinationCraftWorkflow(
			new MinecraftGameActions(),
			100,
			100,
			message -> {},
			message -> failures.incrementAndGet(),
			successes::incrementAndGet
		);

		invokeFail(workflow);

		assertEquals(1, failures.get());
		assertEquals(0, successes.get());
	}

	private void invokeFail(FinalDestinationCraftWorkflow workflow) throws Exception {
		Method fail = FinalDestinationCraftWorkflow.class.getDeclaredMethod("fail", String.class);
		fail.setAccessible(true);
		try {
			fail.invoke(workflow, "expected failure");
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			throw e;
		}
	}
}
