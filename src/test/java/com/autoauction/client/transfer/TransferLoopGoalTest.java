package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferLoopGoalTest {
	@Test
	void accumulatesReceiverDeltasUntilTargetIsReached() {
		TransferLoopGoal goal = new TransferLoopGoal(200_000_000L);

		TransferLoopGoal.Progress first = goal.recordCycle(47_500_000L);
		assertEquals(1, first.cycles());
		assertEquals(47_500_000L, first.totalTransferred());
		assertEquals(152_500_000L, first.remaining());
		assertEquals(4, first.estimatedCyclesRemaining());
		assertFalse(first.complete());

		TransferLoopGoal.Progress second = goal.recordCycle(160_000_000L);
		assertEquals(2, second.cycles());
		assertEquals(207_500_000L, second.totalTransferred());
		assertEquals(0L, second.remaining());
		assertEquals(0, second.estimatedCyclesRemaining());
		assertTrue(second.complete());
	}

	@Test
	void ignoresNegativeCycleDeltaForProgressMath() {
		TransferLoopGoal goal = new TransferLoopGoal(10_000_000L);

		TransferLoopGoal.Progress progress = goal.recordCycle(-500_000L);

		assertEquals(1, progress.cycles());
		assertEquals(0L, progress.totalTransferred());
		assertEquals(10_000_000L, progress.remaining());
		assertEquals(0, progress.estimatedCyclesRemaining());
		assertFalse(progress.complete());
	}
}
