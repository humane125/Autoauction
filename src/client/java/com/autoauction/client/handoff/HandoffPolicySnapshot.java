package com.autoauction.client.handoff;

public record HandoffPolicySnapshot(
	String username,
	String uuid,
	int killLimit,
	String action,
	int stopHours
) {
	public boolean nextAccount() {
		return "NEXT_ACCOUNT".equalsIgnoreCase(action);
	}

	public boolean stopForHours() {
		return "STOP_FOR_HOURS".equalsIgnoreCase(action);
	}

	public boolean disconnectAndWait() {
		return "DISCONNECT_AND_WAIT".equalsIgnoreCase(action);
	}
}
