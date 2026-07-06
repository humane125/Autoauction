package com.autoauction.client.handoff;

public final class HandoffPolicyTriggerGuard {
	private String completedKey = "";
	private String inFlightKey = "";

	public boolean start(String key) {
		String normalized = normalize(key);
		if (normalized.isBlank() || normalized.equals(completedKey) || normalized.equals(inFlightKey)) {
			return false;
		}
		inFlightKey = normalized;
		return true;
	}

	public void complete(String key) {
		String normalized = normalize(key);
		if (normalized.equals(inFlightKey)) {
			completedKey = normalized;
			inFlightKey = "";
		}
	}

	public void fail(String key) {
		String normalized = normalize(key);
		if (normalized.equals(inFlightKey)) {
			inFlightKey = "";
		}
	}

	private String normalize(String key) {
		return key == null ? "" : key.trim().toLowerCase();
	}
}
