package com.autoauction.client;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.automation.AutomationState;
import com.autoauction.client.handoff.HandoffPolicySnapshot;
import com.autoauction.client.macro.NebulaMacroController;
import com.autoauction.client.stats.AccountStatsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	void remoteInstanceActionsDoNotRequireLoadedPlayer() {
		assertEquals(false, AutoauctionClient.remoteActionRequiresLoadedPlayer("reconnect_hypixel"));
		assertEquals(false, AutoauctionClient.remoteActionRequiresLoadedPlayer("close_instance"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresLoadedPlayer("macro_start"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresLoadedPlayer("macro_stop"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresLoadedPlayer("disconnect_server"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresLoadedPlayer("client_command"));
	}

	@Test
	void remoteButtonActionsDoNotRequireContent() {
		assertEquals(false, AutoauctionClient.remoteActionRequiresContent("macro_start"));
		assertEquals(false, AutoauctionClient.remoteActionRequiresContent("macro_stop"));
		assertEquals(false, AutoauctionClient.remoteActionRequiresContent("disconnect_server"));
		assertEquals(false, AutoauctionClient.remoteActionRequiresContent("reconnect_hypixel"));
		assertEquals(false, AutoauctionClient.remoteActionRequiresContent("close_instance"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresContent("server_command"));
		assertEquals(true, AutoauctionClient.remoteActionRequiresContent("text_message"));
	}

	@Test
	void remoteMacroStopSendsToggleWhenMacroIsKnownOn() {
		NebulaMacroController controller = new NebulaMacroController();
		controller.onChatMessage("[Nebula] Combat Macro enabled");
		List<String> commands = new ArrayList<>();

		NebulaMacroController.EnsureResult result = AutoauctionClient.requestRemoteMacroStop(controller, commands::add, 1_000L);

		assertEquals(NebulaMacroController.EnsureResult.PENDING, result);
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
		assertEquals(false, controller.desiredOn());
	}

	@Test
	void remoteMacroStartEnablesAutoRetoggleAfterPreviousStopIntent() {
		NebulaMacroController controller = new NebulaMacroController();
		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		controller.ensureOn(command -> {}, 1_000L);
		controller.recordManualToggleIntent(1_500L);
		List<String> commands = new ArrayList<>();

		NebulaMacroController.EnsureResult result = AutoauctionClient.requestRemoteMacroStart(controller, commands::add, 2_000L);

		assertEquals(NebulaMacroController.EnsureResult.PENDING, result);
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
		assertEquals(true, controller.desiredOn());
	}

	@Test
	void waitsOneSecondAfterClosingBazaarBeforeRemovingArmor() {
		assertEquals(1_000, AutoauctionClient.bazaarCloseDelayMs());
	}

	@Test
	void handoffPolicyCanRunWhileMacroingEvenWhenArmorWatcherStopped() {
		AccountStatsSnapshot macroingSnapshot = new AccountStatsSnapshot(1_000_000, 12_500, 12_500, 12_500, 12_500, true);
		AccountStatsSnapshot idleSnapshot = new AccountStatsSnapshot(1_000_000, 12_500, 12_500, 12_500, 12_500, false);

		assertEquals(true, AutoauctionClient.handoffPolicyCanRun(AutomationState.STOPPED, macroingSnapshot));
		assertEquals(true, AutoauctionClient.handoffPolicyCanRun(AutomationState.WATCHING_ARMOR, macroingSnapshot));
		assertEquals(false, AutoauctionClient.handoffPolicyCanRun(AutomationState.STOPPED, idleSnapshot));
		assertEquals(false, AutoauctionClient.handoffPolicyCanRun(AutomationState.THRESHOLD_REACHED, macroingSnapshot));
	}

	@Test
	void automaticArmorWatcherIsDisabledWhenHandoffPolicyExists() {
		HandoffPolicySnapshot manualPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "DISCONNECT_AND_WAIT", 0);
		HandoffPolicySnapshot schedulerPolicy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			25_000,
			"NEXT_ACCOUNT",
			0,
			"SHORT_ROTATION",
			false,
			"uuid-two",
			false
		);

		assertEquals(true, AutoauctionClient.automaticArmorWatcherEnabled(false, Optional.empty()));
		assertEquals(false, AutoauctionClient.automaticArmorWatcherEnabled(true, Optional.empty()));
		assertEquals(false, AutoauctionClient.automaticArmorWatcherEnabled(false, Optional.of(manualPolicy)));
		assertEquals(false, AutoauctionClient.automaticArmorWatcherEnabled(false, Optional.of(schedulerPolicy)));
	}

	@Test
	void nonSchedulerPolicyCanApplyAfterAutomaticArmorListing() {
		HandoffPolicySnapshot disconnectPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "DISCONNECT_AND_WAIT", 0);
		HandoffPolicySnapshot stopPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "STOP_FOR_HOURS", 2);
		HandoffPolicySnapshot nextPolicy = new HandoffPolicySnapshot("Macro", "uuid-one", 25_000, "NEXT_ACCOUNT", 0);
		HandoffPolicySnapshot schedulerListPolicy = new HandoffPolicySnapshot(
			"Macro",
			"uuid-one",
			25_000,
			"LIST_ARMOR",
			0,
			"FINAL_LISTING",
			true,
			"uuid-two",
			false
		);

		assertEquals(true, AutoauctionClient.shouldApplyPostListingPolicy(disconnectPolicy));
		assertEquals(true, AutoauctionClient.shouldApplyPostListingPolicy(stopPolicy));
		assertEquals(true, AutoauctionClient.shouldApplyPostListingPolicy(nextPolicy));
		assertEquals(false, AutoauctionClient.shouldApplyPostListingPolicy(schedulerListPolicy));
		assertEquals(disconnectPolicy, AutoauctionClient.postListingPolicy(disconnectPolicy));
		assertEquals(null, AutoauctionClient.postListingPolicy(schedulerListPolicy));
	}

	@Test
	void minecraftUsernameComparisonIgnoresCaseAndRejectsBlanks() {
		assertEquals(true, AutoauctionClient.sameMinecraftUsername("SenderOne", "senderone"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("SenderOne", "OtherSender"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("", "OtherSender"));
		assertEquals(false, AutoauctionClient.sameMinecraftUsername("SenderOne", ""));
	}

	@Test
	void accountStatsPolicyRequiresStatsFromCurrentMinecraftAccount() {
		assertEquals(true, AutoauctionClient.accountStatsBelongToCurrentUser("MacroOne", "macroone"));
		assertEquals(false, AutoauctionClient.accountStatsBelongToCurrentUser("MacroOne", "MacroTwo"));
		assertEquals(false, AutoauctionClient.accountStatsBelongToCurrentUser("", "MacroTwo"));
		assertEquals(false, AutoauctionClient.accountStatsBelongToCurrentUser("MacroOne", ""));
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
