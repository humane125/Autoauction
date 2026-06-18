package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarTransferEstimateTest {
	@Test
	void estimatesTransferDeltaFromHypixelQuickStatusAfterTax() {
		BazaarTransferEstimate estimate = BazaarTransferEstimate.fromPrices(
			"MAGMA_CREAM_DISTILLATE",
			16,
			3_500_000L,
			69_993.6,
			6_353.2
		).orElseThrow();

		assertEquals("MAGMA_CREAM_DISTILLATE", estimate.productId());
		assertEquals(16, estimate.quantity());
		assertEquals(1_004_245L, estimate.estimatedDeltaPerCycle());
		assertEquals(4, estimate.estimatedCycles());
	}

	@Test
	void skipsEstimateWhenSpreadCannotTransferCoins() {
		assertTrue(BazaarTransferEstimate.fromPrices("ENCHANTED_DIAMOND", 128, 200_000_000L, 1_000, 1_300).isEmpty());
	}

	@Test
	void convertsDisplayItemNamesToBazaarProductIds() {
		assertEquals("ENCHANTED_DIAMOND", BazaarProductId.fromItemName("Enchanted Diamond"));
		assertEquals("MAGMA_CREAM_DISTILLATE", BazaarProductId.fromItemName("Magma Cream Distillate"));
		assertEquals("RAW_SOULFLOW", BazaarProductId.fromItemName("§9Raw Soulflow"));
	}

	@Test
	void resolvesKnownDistillateNamesAgainstBazaarProductIds() {
		Set<String> productIds = Set.of(
			"GLOWSTONE_DUST_DISTILLATE",
			"MAGMA_CREAM_DISTILLATE",
			"NETHER_STALK_DISTILLATE"
		);

		assertEquals("GLOWSTONE_DUST_DISTILLATE", BazaarProductId.resolveProductId("Glowstone Distillate", productIds));
		assertEquals("MAGMA_CREAM_DISTILLATE", BazaarProductId.resolveProductId("Magma Cream Distillate", productIds));
		assertEquals("NETHER_STALK_DISTILLATE", BazaarProductId.resolveProductId("Nether Wart Distillate", productIds));
	}
}
