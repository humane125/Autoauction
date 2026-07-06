package com.autoauction.client.handoff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandoffPolicyTriggerGuardTest {
	@Test
	void failedInFlightTriggerCanBeRetried() {
		HandoffPolicyTriggerGuard guard = new HandoffPolicyTriggerGuard();

		assertTrue(guard.start("key-one"));
		guard.fail("key-one");

		assertTrue(guard.start("key-one"));
	}

	@Test
	void completedTriggerIsNotStartedAgain() {
		HandoffPolicyTriggerGuard guard = new HandoffPolicyTriggerGuard();

		assertTrue(guard.start("key-one"));
		guard.complete("key-one");

		assertFalse(guard.start("key-one"));
	}
}
