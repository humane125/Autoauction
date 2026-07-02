package com.autoauction.client.handoff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandoffPolicySwitchPreparationTest {
	@Test
	void waitsSendsIslandCommandThenBecomesReady() {
		HandoffPolicySwitchPreparation preparation = new HandoffPolicySwitchPreparation(1_500L, 5_500L);

		assertEquals(HandoffPolicySwitchPreparation.Step.waiting(), preparation.tick(10_000L));
		assertEquals(HandoffPolicySwitchPreparation.Step.waiting(), preparation.tick(11_499L));
		assertEquals(HandoffPolicySwitchPreparation.Step.command("/is"), preparation.tick(11_500L));
		assertEquals(HandoffPolicySwitchPreparation.Step.waiting(), preparation.tick(16_999L));
		assertEquals(HandoffPolicySwitchPreparation.Step.ready(), preparation.tick(17_000L));
		assertEquals(HandoffPolicySwitchPreparation.Step.ready(), preparation.tick(20_000L));
	}
}
