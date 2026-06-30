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
		assertEquals(Optional.of(new SummoningEyeEvent("sell_order", 1, 1_500_000)),
			SummoningEyeEventDetector.detect("[Bazaar] Sell Order Setup! 1x Summoning Eye at 1,500,000 coins each"));
	}
}
