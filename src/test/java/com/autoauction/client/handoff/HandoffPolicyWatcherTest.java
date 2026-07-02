package com.autoauction.client.handoff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandoffPolicyWatcherTest {
	@Test
	void doesNothingBelowPolicyLimit() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 12_500, "NEXT_ACCOUNT", 0);

		assertEquals(HandoffPolicyWatcher.Decision.NONE, watcher.decide(12_499, policy));
	}

	@Test
	void handsOffWithoutListingAtPolicyLimitBelowTwentyFiveThousand() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 12_500, "NEXT_ACCOUNT", 0);

		assertEquals(HandoffPolicyWatcher.Decision.NON_LISTING_HANDOFF, watcher.decide(12_500, policy));
	}

	@Test
	void defersToArmorListingAtTwentyFiveThousand() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 12_500, "NEXT_ACCOUNT", 0);

		assertEquals(HandoffPolicyWatcher.Decision.LIST_ARMOR, watcher.decide(25_000, policy));
	}

	@Test
	void schedulerNextAccountPolicyDoesNotAutoListAtTwentyFiveThousand() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			25_000,
			"NEXT_ACCOUNT",
			0,
			"SHORT_ROTATION",
			false,
			"uuid-two",
			false
		);

		assertEquals(HandoffPolicyWatcher.Decision.NON_LISTING_HANDOFF, watcher.decide(25_000, policy));
	}

	@Test
	void finalListingSchedulerPolicyListsAtPolicyLimit() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			30_000,
			"LIST_ARMOR",
			0,
			"FINAL_LISTING",
			true,
			"uuid-two",
			false
		);

		assertEquals(HandoffPolicyWatcher.Decision.NONE, watcher.decide(29_999, policy));
		assertEquals(HandoffPolicyWatcher.Decision.LIST_ARMOR, watcher.decide(30_000, policy));
	}
}
