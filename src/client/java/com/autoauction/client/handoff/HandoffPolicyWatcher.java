package com.autoauction.client.handoff;

public final class HandoffPolicyWatcher {
	public static final int ARMOR_LISTING_KILL_LIMIT = 25_000;

	public Decision decide(int lowestFinalDestinationKills, HandoffPolicySnapshot policy) {
		if (policy == null) {
			return Decision.NONE;
		}
		if (policy.schedulerPolicy() && policy.craftReforgeArmor()) {
			return Decision.CRAFT_REFORGE_ARMOR;
		}
		if (lowestFinalDestinationKills < policy.killLimit()) {
			return Decision.NONE;
		}
		if (policy.schedulerPolicy()) {
			return policy.finalListing() || policy.listArmor()
				? Decision.LIST_ARMOR
				: Decision.NON_LISTING_HANDOFF;
		}
		boolean policyAllowsArmorListing = policy.killLimit() >= ARMOR_LISTING_KILL_LIMIT;
		if ((policy.finalListing() || policy.listArmor()) && !policyAllowsArmorListing) {
			return Decision.NONE;
		}
		if (policyAllowsArmorListing && lowestFinalDestinationKills >= ARMOR_LISTING_KILL_LIMIT) {
			return Decision.LIST_ARMOR;
		}
		return Decision.NON_LISTING_HANDOFF;
	}

	public enum Decision {
		NONE,
		NON_LISTING_HANDOFF,
		LIST_ARMOR,
		CRAFT_REFORGE_ARMOR
	}
}
