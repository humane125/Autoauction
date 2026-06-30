package com.autoauction.client.stats;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummoningEyeEventDetectorTest {
	@Test
	void detectsSummoningEyeDropAndBazaarSales() {
		assertEquals(Optional.of(new SummoningEyeEvent("drop", 1, 0)),
			SummoningEyeEventDetector.detect("RARE DROP! Summoning Eye"));
		assertEquals(Optional.of(new SummoningEyeEvent("instant_sell", 2, 0)),
			SummoningEyeEventDetector.detect("[Bazaar] Sold 2x Summoning Eye for 2,800,000 coins!"));
		assertEquals(Optional.of(new SummoningEyeEvent("sell_order", 2, 1_500_000)),
			SummoningEyeEventDetector.detect("[Bazaar] Sell Offer Setup! 2x Summoning Eye for 3,000,000 coins"));
		assertEquals(Optional.of(new SummoningEyeEvent("claimed", 8, 1_607_582)),
			SummoningEyeEventDetector.detect("[Bazaar] Claimed 12,699,897 coins from selling 8x Summoning Eye at 1,607,582 each!"));
		assertEquals(Optional.of(new SummoningEyeEvent("cancelled", 10, 0)),
			SummoningEyeEventDetector.detect("[Bazaar] Cancelled! Refunded 10x Summoning Eye from cancelling Sell Offer!"));
	}
}
