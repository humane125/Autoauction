package com.autoauction.client.handoff;

public final class HandoffPolicyWatcher {
	public static final int ARMOR_LISTING_KILL_LIMIT = 25_000;

	public Decision decide(int lowestFinalDestinationKills, HandoffPolicySnapshot policy) {
		if (policy == null || lowestFinalDestinationKills < policy.killLimit()) {
			return Decision.NONE;
		}
		if (lowestFinalDestinationKills >= ARMOR_LISTING_KILL_LIMIT) {
			return Decision.LIST_ARMOR;
		}
		return Decision.NON_LISTING_HANDOFF;
	}

	public enum Decision {
		NONE,
		NON_LISTING_HANDOFF,
		LIST_ARMOR
	}
}
