package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderChestParkingPlanTest {
	@Test
	void capsBySenderPurseAndRoundsTransferToFullStacks() {
		EnderChestParkingPlan plan = EnderChestParkingPlan.create(
			512,
			200_000_000L,
			0L,
			70_000_000D / 64D,
			8
		);

		assertTrue(plan.canRun());
		assertEquals(128, plan.transferQuantity());
		assertEquals(384, plan.parkQuantity());
		assertEquals(6, plan.requiredParkSlots());
	}

	@Test
	void capsQuantityByRemainingTargetProfit() {
		EnderChestParkingPlan plan = EnderChestParkingPlan.createForTarget(
			512,
			200_000_000L,
			0L,
			100_000D,
			25_000D,
			6_400_000L,
			8
		);

		assertTrue(plan.canRun());
		assertEquals(256, plan.transferQuantity());
		assertEquals(256, plan.parkQuantity());
		assertEquals(4, plan.requiredParkSlots());
	}

	@Test
	void reusesAlreadyParkedStacksAsAvailableQuantityAndCapacity() {
		EnderChestParkingPlan plan = EnderChestParkingPlan.createForPersistentParking(
			0,
			6,
			200_000_000L,
			0L,
			100_000D,
			25_000D,
			3_200_000L,
			0
		);

		assertTrue(plan.canRun());
		assertEquals(128, plan.transferQuantity());
		assertEquals(256, plan.parkQuantity());
		assertEquals(4, plan.requiredParkSlots());
	}

	@Test
	void stopsWhenSafeQuantityIsBelowOneStack() {
		EnderChestParkingPlan plan = EnderChestParkingPlan.create(
			128,
			1_000_000L,
			0L,
			50_000D,
			8
		);

		assertEquals(EnderChestParkingPlan.StopReason.BELOW_STACK, plan.stopReason());
		assertEquals(0, plan.transferQuantity());
	}

	@Test
	void stopsBeforeMovingWhenEnderChestHasTooLittleSpace() {
		EnderChestParkingPlan plan = EnderChestParkingPlan.create(
			512,
			200_000_000L,
			0L,
			70_000_000D / 64D,
			2
		);

		assertEquals(EnderChestParkingPlan.StopReason.NOT_ENOUGH_EC_SPACE, plan.stopReason());
		assertEquals(0, plan.transferQuantity());
	}
}
