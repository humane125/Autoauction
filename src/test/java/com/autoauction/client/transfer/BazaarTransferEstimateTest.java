package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarTransferEstimateTest {
	@Test
	void estimatesTransferDeltaFromBuyAndSellOrderSpreadAfterTax() {
		BazaarTransferEstimate estimate = BazaarTransferEstimate.fromPrices(
			"ENCHANTED_DIAMOND",
			128,
			200_000_000L,
			1_000,
			1_300
		).orElseThrow();

		assertEquals("ENCHANTED_DIAMOND", estimate.productId());
		assertEquals(128, estimate.quantity());
		assertEquals(36_295L, estimate.estimatedDeltaPerCycle());
		assertEquals(5_511, estimate.estimatedCycles());
	}

	@Test
	void skipsEstimateWhenSpreadCannotTransferCoins() {
		assertTrue(BazaarTransferEstimate.fromPrices("ENCHANTED_DIAMOND", 128, 200_000_000L, 1_300, 1_000).isEmpty());
	}

	@Test
	void convertsDisplayItemNamesToBazaarProductIds() {
		assertEquals("ENCHANTED_DIAMOND", BazaarProductId.fromItemName("Enchanted Diamond"));
		assertEquals("MAGMA_CREAM_DISTILLATE", BazaarProductId.fromItemName("Magma Cream Distillate"));
		assertEquals("RAW_SOULFLOW", BazaarProductId.fromItemName("§9Raw Soulflow"));
	}
}
