package com.autoauction.client.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftGameActionsTest {
	@Test
	void normalizesCommandsForClientDispatcher() {
		assertEquals("macro start", MinecraftGameActions.normalizedCommand("macro start"));
		assertEquals("macro start", MinecraftGameActions.normalizedCommand("/macro start"));
		assertEquals("", MinecraftGameActions.normalizedCommand("   "));
	}

	@Test
	void detectsBazaarInventorySellScreensAndItems() {
		assertTrue(MinecraftGameActions.isBazaarTitle("Bazaar \u279c Mining"));
		assertTrue(MinecraftGameActions.isBazaarTitle("Bazaar \u279c Farming"));
		assertTrue(MinecraftGameActions.isSellInventoryButtonName("Sell Inventory Now"));
		assertTrue(MinecraftGameActions.isSellInventoryConfirmTitle("Are you sure?"));
		assertTrue(MinecraftGameActions.isSellInventoryConfirmName("Selling whole inventory"));

		assertFalse(MinecraftGameActions.isBazaarTitle("Auction House"));
		assertFalse(MinecraftGameActions.isSellInventoryButtonName("Bazaar Settings"));
		assertFalse(MinecraftGameActions.isSellInventoryConfirmTitle("Create BIN Auction"));
		assertFalse(MinecraftGameActions.isSellInventoryConfirmName("Cancel"));
	}
}
