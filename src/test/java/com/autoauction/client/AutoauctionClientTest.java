package com.autoauction.client;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoauctionClientTest {
	@Test
	void testListingUsesFixedLowPrices() {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		EnumMap<ArmorPiece, String> inventoryNames = new EnumMap<>(ArmorPiece.class);
		for (ArmorPiece piece : ArmorPiece.values()) {
			armor.put(piece, new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), 25_000, false, 0));
			inventoryNames.put(piece, "Testing " + piece.baseName());
		}

		var pricedArmor = AutoauctionClient.testListingPrices(armor, inventoryNames).join();

		assertEquals(ArmorPiece.values().length, pricedArmor.size());
		for (AutoauctionClient.PricedArmor item : pricedArmor) {
			assertEquals(2_500_000, item.price());
			assertEquals(inventoryNames.get(item.armor().piece()), item.inventoryItemName());
		}
	}

	@Test
	void clickDelayHasHardMinimumOfFourHundredMs() {
		assertEquals(400, AutoauctionClient.effectiveClickDelayMs(0));
		assertEquals(400, AutoauctionClient.effectiveClickDelayMs(250));
		assertEquals(750, AutoauctionClient.effectiveClickDelayMs(750));
	}

	@Test
	void waitsOneAndHalfSecondsAfterProxyReadyBeforeReconnect() {
		long proxyReadyAt = 10_000L;

		assertEquals(false, AutoauctionClient.proxyReadyDelayElapsed(proxyReadyAt, 11_499L));
		assertEquals(true, AutoauctionClient.proxyReadyDelayElapsed(proxyReadyAt, 11_500L));
	}

	@Test
	void waitsHalfSecondAfterHypixelPlayerLoadedBeforeMacroStart() {
		long hypixelReadyAt = 20_000L;

		assertEquals(false, AutoauctionClient.hypixelReadyDelayElapsed(hypixelReadyAt, 20_499L));
		assertEquals(true, AutoauctionClient.hypixelReadyDelayElapsed(hypixelReadyAt, 20_500L));
	}

	@Test
	void waitsFiveAndHalfSecondsAfterIslandReturnBeforeMoreCommands() {
		assertEquals(5_500, AutoauctionClient.islandCommandCooldownDelayMs());
	}
}
