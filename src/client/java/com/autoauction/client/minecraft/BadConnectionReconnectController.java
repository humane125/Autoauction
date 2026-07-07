package com.autoauction.client.minecraft;

import java.util.Locale;
import java.util.function.LongSupplier;

public final class BadConnectionReconnectController {
	public static final long MIN_RECONNECT_DELAY_MS = 10_000L;
	public static final long MAX_RECONNECT_DELAY_MS = 20_000L;
	private static final long INTENTIONAL_DISCONNECT_SUPPRESS_MS = 30_000L;

	private long suppressUntilMs;
	private long pendingReconnectAtMs;
	private String pendingReason = "";

	public void suppressIntentionalDisconnects(long nowMs) {
		suppressUntilMs = Math.max(suppressUntilMs, nowMs + INTENTIONAL_DISCONNECT_SUPPRESS_MS);
		clearPendingReconnect();
	}

	public boolean scheduleIfBadConnection(String reason, boolean banned, long nowMs, LongSupplier delayMsSupplier) {
		if (banned || nowMs < suppressUntilMs || !isBadConnectionKick(reason)) {
			return false;
		}
		long delayMs = Math.max(MIN_RECONNECT_DELAY_MS, Math.min(MAX_RECONNECT_DELAY_MS, delayMsSupplier.getAsLong()));
		pendingReconnectAtMs = nowMs + delayMs;
		pendingReason = String.valueOf(reason == null ? "" : reason);
		return true;
	}

	public boolean reconnectDue(long nowMs) {
		return pendingReconnectAtMs > 0L && nowMs >= pendingReconnectAtMs;
	}

	public void clearPendingReconnect() {
		pendingReconnectAtMs = 0L;
		pendingReason = "";
	}

	public boolean hasPendingReconnect() {
		return pendingReconnectAtMs > 0L;
	}

	public long pendingReconnectAt() {
		return pendingReconnectAtMs;
	}

	public String pendingReason() {
		return pendingReason;
	}

	public static boolean isBadConnectionKick(String reason) {
		String normalized = String.valueOf(reason == null ? "" : reason).toLowerCase(Locale.ROOT);
		return normalized.contains("timed out")
			|| normalized.contains("connection reset")
			|| normalized.contains("forcibly closed")
			|| normalized.contains("bad connection")
			|| normalized.contains("socketexception")
			|| normalized.contains("connection lost");
	}
}
