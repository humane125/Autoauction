package com.autoauction.client.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionBlockedMessageDetectorTest {
	@Test
	void detectsCookieBuffAuctionBlock() {
		assertTrue(AuctionBlockedMessageDetector.isCookieBuffRequired(
			"You need the Cookie Buff to use this command! Obtain a Booster Cookie from the Community Shop in the hub!"
		));
	}

	@Test
	void ignoresUnrelatedMessages() {
		assertFalse(AuctionBlockedMessageDetector.isCookieBuffRequired("AutoAuction priced Final Destination Boots at 26000000"));
	}
}
