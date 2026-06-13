package com.autoauction.client.domain;

public final class AuctionBlockedMessageDetector {
	private AuctionBlockedMessageDetector() {
	}

	public static boolean isCookieBuffRequired(String message) {
		String normalized = message.toLowerCase();
		return normalized.contains("you need the cookie buff to use this command")
			|| normalized.contains("obtain a booster cookie from the community shop");
	}
}
