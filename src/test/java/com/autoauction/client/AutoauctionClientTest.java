package com.autoauction.client;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.macro.NebulaMacroController;
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
	void realArmorListingUsesFixedPriceWithoutRecommendations() {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		EnumMap<ArmorPiece, String> inventoryNames = new EnumMap<>(ArmorPiece.class);
		for (ArmorPiece piece : ArmorPiece.values()) {
			armor.put(piece, new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), 25_000, false, 0));
			inventoryNames.put(piece, "Equipped " + piece.baseName());
		}

		var pricedArmor = AutoauctionClient.realListingPrices(armor, inventoryNames).join();

		assertEquals(ArmorPiece.values().length, pricedArmor.size());
		for (AutoauctionClient.PricedArmor item : pricedArmor) {
			assertEquals(24_999_000, item.price());
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

	@Test
	void waitsOneSecondAfterClosingBazaarBeforeRemovingArmor() {
		assertEquals(1_000, AutoauctionClient.bazaarCloseDelayMs());
	}

	@Test
	void minecraftUsernameComparisonIgnoresCaseAndRejectsBlanks() {
		assertEquals(true, AutoauctionClient.sameMinecraftUsername("SenderOne", "senderone"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("SenderOne", "OtherSender"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("", "OtherSender"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("SenderOne", ""));
	}

	@Test
	void retoggleStatusShowsCountObservedAndDesiredState() {
		assertEquals(
			"AutoAuction retoggle status: count=3, lobbyCollisions=2, observed=OFF, desired=ON.",
			AutoauctionClient.retoggleStatusMessage(3, 2, NebulaMacroController.ObservedState.OFF, true)
		);
		assertEquals(
			"AutoAuction retoggle status: count=0, lobbyCollisions=0, observed=UNKNOWN, desired=OFF.",
			AutoauctionClient.retoggleStatusMessage(0, 0, NebulaMacroController.ObservedState.UNKNOWN, false)
		);
	}

	@Test
	void nebulaConfigDebugMessagesDoNotIncludeLocalPaths() {
		assertEquals("Loaded Nebula GUI key code 344.", AutoauctionClient.nebulaGuiKeyLoadedMessage(344));
		assertEquals("Loaded Nebula combat macro key code 80.", AutoauctionClient.nebulaCombatMacroKeyLoadedMessage(80));
		assertEquals("Could not load Nebula GUI key.", AutoauctionClient.nebulaGuiKeyLoadFailedMessage());
		assertEquals(
			"Could not load Nebula combat macro key; falling back to autoauction.json.",
			AutoauctionClient.nebulaCombatMacroKeyLoadFailedMessage()
		);
	}
}
