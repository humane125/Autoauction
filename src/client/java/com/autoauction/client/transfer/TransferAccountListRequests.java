package com.autoauction.client.transfer;

import java.util.concurrent.atomic.AtomicInteger;

public final class TransferAccountListRequests {
	private final AtomicInteger silentResponses = new AtomicInteger();

	public void markSilentRequestSent() {
		silentResponses.incrementAndGet();
	}

	public boolean consumeSilentResponse() {
		while (true) {
			int current = silentResponses.get();
			if (current <= 0) {
				return false;
			}
			if (silentResponses.compareAndSet(current, current - 1)) {
				return true;
			}
		}
	}
}
