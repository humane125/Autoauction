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
	void manualPolicyBelowTwentyFiveThousandDoesNotAutoListAtTwentyFiveThousand() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 12_500, "NEXT_ACCOUNT", 0);

		assertEquals(HandoffPolicyWatcher.Decision.NON_LISTING_HANDOFF, watcher.decide(25_000, policy));
	}

	@Test
	void manualPolicyAtTwentyFiveThousandListsBeforeApplyingPolicyAction() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot disconnectPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "DISCONNECT_AND_WAIT", 0);
		HandoffPolicySnapshot nextPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "NEXT_ACCOUNT", 0);

		assertEquals(HandoffPolicyWatcher.Decision.NONE, watcher.decide(24_999, disconnectPolicy));
		assertEquals(HandoffPolicyWatcher.Decision.LIST_ARMOR, watcher.decide(25_000, disconnectPolicy));
		assertEquals(HandoffPolicyWatcher.Decision.LIST_ARMOR, watcher.decide(25_000, nextPolicy));
	}

	@Test
	void manualListArmorPolicyBelowTwentyFiveThousandWaitsInsteadOfListingEarly() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 12_500, "LIST_ARMOR", 0);

		assertEquals(HandoffPolicyWatcher.Decision.NONE, watcher.decide(12_500, policy));
		assertEquals(HandoffPolicyWatcher.Decision.NONE, watcher.decide(25_000, policy));
	}

	@Test
	void manualListArmorPolicyAtTwentyFiveThousandLists() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "LIST_ARMOR", 0);

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

	@Test
	void schedulerCraftReforgeRunsAsImmediateAction() {
		HandoffPolicyWatcher watcher = new HandoffPolicyWatcher();
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			1,
			"CRAFT_REFORGE_ARMOR",
			0,
			"SHORT_ROTATION",
			false,
			"",
			false,
			"trigger",
			"Fierce",
			"",
			""
		);

		assertEquals(HandoffPolicyWatcher.Decision.CRAFT_REFORGE_ARMOR, watcher.decide(0, policy));
	}

	@Test
	void listArmorPolicyCanExposeCraftReforgeFollowUp() {
		HandoffPolicySnapshot policy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			25_000,
			"LIST_ARMOR",
			0,
			"FINAL_LISTING",
			true,
			"uuid-two",
			false,
			"trigger",
			"",
			"CRAFT_REFORGE_ARMOR",
			"Wise"
		);

		assertEquals(true, policy.followUpCraftReforgeArmor());
		assertEquals("Wise", policy.followUpReforge());
	}
}
