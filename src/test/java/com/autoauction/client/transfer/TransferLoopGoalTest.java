package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferLoopGoalTest {
	@Test
	void accumulatesReceiverDeltasUntilTargetIsReached() {
		TransferLoopGoal goal = new TransferLoopGoal(200_000_000L);

		TransferLoopGoal.Progress first = goal.recordCycle(47_500_000L, 1_000_000L, 48_500_000L);
		assertEquals(1, first.cycles());
		assertEquals(47_500_000L, first.totalTransferred());
		assertEquals(1_000_000L, first.startingPurse());
		assertEquals(48_500_000L, first.donePurse());
		assertEquals(47_500_000L, first.totalPurseDelta());
		assertEquals(152_500_000L, first.remaining());
		assertEquals(4, first.estimatedCyclesRemaining());
		assertEquals(152_500_000L, goal.remainingCoins());
		assertFalse(first.complete());

		TransferLoopGoal.Progress second = goal.recordCycle(160_000_000L, 48_500_000L, 208_500_000L);
		assertEquals(2, second.cycles());
		assertEquals(207_500_000L, second.totalTransferred());
		assertEquals(1_000_000L, second.startingPurse());
		assertEquals(208_500_000L, second.donePurse());
		assertEquals(207_500_000L, second.totalPurseDelta());
		assertEquals(0L, second.remaining());
		assertEquals(0, second.estimatedCyclesRemaining());
		assertEquals(0L, goal.remainingCoins());
		assertTrue(second.complete());
	}

	@Test
	void ignoresNegativeCycleDeltaForProgressMath() {
		TransferLoopGoal goal = new TransferLoopGoal(10_000_000L);

		TransferLoopGoal.Progress progress = goal.recordCycle(-500_000L, 10_000_000L, 9_500_000L);

		assertEquals(1, progress.cycles());
		assertEquals(0L, progress.totalTransferred());
		assertEquals(10_000_000L, progress.startingPurse());
		assertEquals(9_500_000L, progress.donePurse());
		assertEquals(-500_000L, progress.totalPurseDelta());
		assertEquals(10_000_000L, progress.remaining());
		assertEquals(0, progress.estimatedCyclesRemaining());
		assertFalse(progress.complete());
	}
}
