package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarTransferWorkflowTest {
	@Test
	void definesReceiverBuyOrderSlotsFromDump() {
		assertEquals(15, BazaarTransferWorkflow.CREATE_BUY_ORDER_SLOT);
		assertEquals(16, BazaarTransferWorkflow.CUSTOM_AMOUNT_SLOT);
		assertEquals(12, BazaarTransferWorkflow.TOP_ORDER_PLUS_0_1_SLOT);
		assertEquals(13, BazaarTransferWorkflow.CONFIRM_BUY_ORDER_SLOT);
		assertEquals(11, BazaarTransferWorkflow.SELL_INSTANTLY_SLOT);
		assertEquals(13, BazaarTransferWorkflow.INSTANT_SELL_WARNING_SLOT);
		assertEquals(50, BazaarTransferWorkflow.MANAGE_ORDERS_SLOT);
		assertEquals(16, BazaarTransferWorkflow.CREATE_SELL_OFFER_SLOT);
		assertEquals(12, BazaarTransferWorkflow.BEST_OFFER_MINUS_0_1_SLOT);
		assertEquals(13, BazaarTransferWorkflow.CONFIRM_SELL_OFFER_SLOT);
		assertEquals(10, BazaarTransferWorkflow.BUY_INSTANTLY_SLOT);
		assertEquals(13, BazaarTransferWorkflow.CONFIRM_INSTANT_BUY_SLOT);
		assertEquals("Confirm", BazaarTransferWorkflow.CONFIRM_INSTANT_BUY_READY_NAME);
		assertEquals(32, BazaarTransferWorkflow.CLAIM_ALL_COINS_SLOT);
	}

	@Test
	void detectsReceiverBuyOrderScreens() {
		assertTrue(BazaarTransferWorkflow.isItemPage("Combat \u279c Ender Pearl", "Ender Pearl"));
		assertTrue(BazaarTransferWorkflow.isItemPage("Combat \u279c Ender Pearl", "enderpearl"));
		assertTrue(BazaarTransferWorkflow.isItemPage("Inferno Demonlord \u279c Blaze Rod", "Blaze Rod Distillate"));
		assertTrue(BazaarTransferWorkflow.isItemPage("Inferno Demonlord \u279c Blaze Rod D", "Blaze Rod Distillate"));
		assertTrue(BazaarTransferWorkflow.isBazaarResultScreen("Bazaar \u279c "));
		assertTrue(BazaarTransferWorkflow.isBazaarResultScreen("Bazaar \u279c \"Ender Pearl\""));
		assertTrue(BazaarTransferWorkflow.isBuyOrderAmountScreen("How many do you want?"));
		assertTrue(BazaarTransferWorkflow.isBuyOrderPriceScreen("How much do you want to pay?"));
		assertTrue(BazaarTransferWorkflow.isConfirmBuyOrderScreen("Confirm Buy Order"));
		assertTrue(BazaarTransferWorkflow.isInstantSellWarningScreen("Confirm"));
		assertTrue(BazaarTransferWorkflow.isInstantBuyAmountScreen("Magma Cream Distillate \u279c Instant Buy"));
		assertTrue(BazaarTransferWorkflow.isInstantBuyAmountScreen("Magma Cream Distillate \u279c Instan"));
		assertTrue(BazaarTransferWorkflow.isConfirmInstantBuyScreen("Confirm Instant Buy"));
		assertTrue(BazaarTransferWorkflow.isOrdersScreen("Your Bazaar Orders"));
		assertTrue(BazaarTransferWorkflow.isSellOfferPriceScreen("At what price are you selling?"));
		assertTrue(BazaarTransferWorkflow.isConfirmSellOfferScreen("Confirm Sell Offer"));
		assertTrue(BazaarTransferWorkflow.isSignScreen("Edit Sign Message"));

		assertFalse(BazaarTransferWorkflow.isItemPage("Bazaar \u279c ", "Ender Pearl"));
		assertFalse(BazaarTransferWorkflow.isItemPage("Bazaar \u279c \"Ender Pearl\"", "Ender Pearl"));
		assertFalse(BazaarTransferWorkflow.isItemPage("Ender Pearl \u279c Instant Buy", "Ender Pearl"));
		assertFalse(BazaarTransferWorkflow.isBazaarResultScreen("Combat \u279c Ender Pearl"));
		assertFalse(BazaarTransferWorkflow.isBuyOrderAmountScreen("At what price are you selling?"));
		assertFalse(BazaarTransferWorkflow.isBuyOrderPriceScreen("How many do you want?"));
		assertFalse(BazaarTransferWorkflow.isConfirmBuyOrderScreen("Cancel Buy Order"));
		assertFalse(BazaarTransferWorkflow.isInstantSellWarningScreen("Confirm Buy Order"));
		assertFalse(BazaarTransferWorkflow.isInstantBuyAmountScreen("Confirm Instant Buy"));
		assertFalse(BazaarTransferWorkflow.isConfirmInstantBuyScreen("Confirm Buy Order"));
		assertFalse(BazaarTransferWorkflow.isOrdersScreen("Confirm Sell Offer"));
		assertFalse(BazaarTransferWorkflow.isSellOfferPriceScreen("How much do you want to pay?"));
		assertFalse(BazaarTransferWorkflow.isConfirmSellOfferScreen("Confirm Buy Order"));
	}

	@Test
	void stripsFormattingAndDetectsFilledSellOfferChat() {
		String message = "\u00a76[Bazaar] \u00a7eYour \u00a76Sell Offer \u00a7efor \u00a7a1\u00a77x \u00a79Magma Cream Distillate \u00a7ewas filled!";

		assertEquals("[Bazaar] Your Sell Offer for 1x Magma Cream Distillate was filled!", BazaarTransferWorkflow.cleanChatMessage(message));
		assertTrue(BazaarTransferWorkflow.isSellOfferFilledMessage(message, "Magma Cream Distillate"));
		assertTrue(BazaarTransferWorkflow.isSellOfferFilledMessage(message, "magma cream distillate"));
		assertFalse(BazaarTransferWorkflow.isSellOfferFilledMessage(message, "Blaze Rod Distillate"));
	}

	@Test
	void stripsFormattingAndDetectsInstantBuyCompleteChat() {
		String section = "\u00a7";
		String message = section + "6[Bazaar] " + section + "eBought " + section + "7384" + section + "7x "
			+ section + "aWet Water " + section + "efor " + section + "61,343,347 coins" + section + "e!";

		assertEquals("[Bazaar] Bought 384x Wet Water for 1,343,347 coins!", BazaarTransferWorkflow.cleanChatMessage(message));
		assertTrue(BazaarTransferWorkflow.isInstantBuyCompleteMessage(message, "Wet Water"));
		assertTrue(BazaarTransferWorkflow.isInstantBuyCompleteMessage(message, "wet water"));
		assertFalse(BazaarTransferWorkflow.isInstantBuyCompleteMessage(message, "Ender Pearl"));
	}

	@Test
	void stripsFormattingAndDetectsFilledBuyOrderChat() {
		String message = "\u00a76[Bazaar] \u00a7eYour \u00a7aBuy Order \u00a7efor \u00a7a1\u00a77x \u00a79Blaze Rod Distillate \u00a7ewas filled!";

		assertEquals("[Bazaar] Your Buy Order for 1x Blaze Rod Distillate was filled!", BazaarTransferWorkflow.cleanChatMessage(message));
		assertTrue(BazaarTransferWorkflow.isBuyOrderFilledMessage(message, "Blaze Rod Distillate"));
		assertTrue(BazaarTransferWorkflow.isBuyOrderFilledMessage(message, "blaze rod distillate"));
		assertFalse(BazaarTransferWorkflow.isBuyOrderFilledMessage(message, "Ender Pearl"));
	}

	@Test
	void normalizesQuantityForSignInput() {
		assertEquals("128", BazaarTransferWorkflow.quantityText(128));
		assertEquals("1", BazaarTransferWorkflow.quantityText(0));
		assertEquals("1", BazaarTransferWorkflow.quantityText(-5));
	}
}
