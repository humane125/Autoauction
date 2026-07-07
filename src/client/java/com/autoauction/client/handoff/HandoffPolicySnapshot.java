package com.autoauction.client.handoff;

public record HandoffPolicySnapshot(
	String username,
	String uuid,
	int killLimit,
	String action,
	int stopHours,
	String phase,
	boolean finalListing,
	String nextTarget,
	boolean waitAfterHandoff,
	String triggerKey,
	String reforge,
	String followUpAction,
	String followUpReforge
) {
	public HandoffPolicySnapshot(String username, String uuid, int killLimit, String action, int stopHours) {
		this(username, uuid, killLimit, action, stopHours, "", false, "", false, "", "", "", "");
	}

	public HandoffPolicySnapshot(
		String username,
		String uuid,
		int killLimit,
		String action,
		int stopHours,
		String phase,
		boolean finalListing,
		String nextTarget,
		boolean waitAfterHandoff
	) {
		this(username, uuid, killLimit, action, stopHours, phase, finalListing, nextTarget, waitAfterHandoff, "");
	}

	public HandoffPolicySnapshot(
		String username,
		String uuid,
		int killLimit,
		String action,
		int stopHours,
		String phase,
		boolean finalListing,
		String nextTarget,
		boolean waitAfterHandoff,
		String triggerKey
	) {
		this(username, uuid, killLimit, action, stopHours, phase, finalListing, nextTarget, waitAfterHandoff,
			triggerKey, "", "", "");
	}

	public boolean nextAccount() {
		return "NEXT_ACCOUNT".equalsIgnoreCase(action);
	}

	public boolean stopForHours() {
		return "STOP_FOR_HOURS".equalsIgnoreCase(action);
	}

	public boolean disconnectAndWait() {
		return "DISCONNECT_AND_WAIT".equalsIgnoreCase(action);
	}

	public boolean listArmor() {
		return "LIST_ARMOR".equalsIgnoreCase(action);
	}

	public boolean craftReforgeArmor() {
		return "CRAFT_REFORGE_ARMOR".equalsIgnoreCase(action);
	}

	public boolean followUpCraftReforgeArmor() {
		return "CRAFT_REFORGE_ARMOR".equalsIgnoreCase(followUpAction);
	}

	public String craftReforge() {
		return reforge == null || reforge.isBlank() ? "Fierce" : reforge;
	}

	public String followUpReforge() {
		return followUpReforge == null || followUpReforge.isBlank() ? "Fierce" : followUpReforge;
	}

	public boolean schedulerPolicy() {
		return phase != null && !phase.isBlank();
	}
}
