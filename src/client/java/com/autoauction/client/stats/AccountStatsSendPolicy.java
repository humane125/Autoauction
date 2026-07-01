package com.autoauction.client.stats;

public final class AccountStatsSendPolicy {
	private AccountStatsSendPolicy() {
	}

	public static boolean shouldSend(
		AccountStatsSnapshot snapshot,
		AccountStatsSnapshot lastSentSnapshot,
		long lastSentAt,
		long now,
		long intervalMs
	) {
		if (snapshot == null) {
			return false;
		}
		if (lastSentSnapshot == null) {
			return true;
		}
		if (snapshot.macroing() != lastSentSnapshot.macroing()) {
			return true;
		}
		return now - lastSentAt >= intervalMs;
	}
}
