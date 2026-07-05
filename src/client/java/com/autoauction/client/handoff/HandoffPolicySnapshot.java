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
	String triggerKey
) {
	public HandoffPolicySnapshot(String username, String uuid, int killLimit, String action, int stopHours) {
		this(username, uuid, killLimit, action, stopHours, "", false, "", false, "");
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

	public boolean schedulerPolicy() {
		return phase != null && !phase.isBlank();
	}
}
