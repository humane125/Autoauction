package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NebulaMacroControllerTest {
	@Test
	void detectsEnabledAndDisabledMessagesAfterStrippingFormattingAndLowercasing() {
		NebulaMacroController controller = new NebulaMacroController();

		controller.onChatMessage("§bNebulaClient §7» §aCombat Macro: Enabled");
		assertEquals(NebulaMacroController.ObservedState.ON, controller.observedState());

		controller.onChatMessage("§bNEBULACLIENT §7» §cCOMBAT MACRO: DISABLED");
		assertEquals(NebulaMacroController.ObservedState.OFF, controller.observedState());
	}

	@Test
	void detectsCompactCombatMacroMessages() {
		NebulaMacroController controller = new NebulaMacroController();

		controller.onChatMessage("NebulaClient > CombatMacro:Enabled");
		assertEquals(NebulaMacroController.ObservedState.ON, controller.observedState());

		controller.onChatMessage("NebulaClient > CombatMacro:Disabled");
		assertEquals(NebulaMacroController.ObservedState.OFF, controller.observedState());
	}

	@Test
	void ignoresAutoAuctionDebugEchoesOfNebulaMacroMessages() {
		NebulaMacroController controller = new NebulaMacroController();

		controller.onChatMessage("[AutoAuction debug/nebula] NebulaClient > Combat Macro: Enabled");

		assertEquals(NebulaMacroController.ObservedState.UNKNOWN, controller.observedState());
	}

	@Test
	void ensureOnTogglesWhenOffAndCompletesAfterEnabledChat() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();
		controller.onChatMessage("NebulaClient » Combat Macro: Disabled");

		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOn(commands::add, 1_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);

		controller.onChatMessage("NebulaClient » Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_100L));
		assertEquals(1, commands.size());
	}

	@Test
	void ensureOffFromUnknownTogglesOnThenOffWhenFirstToggleEnablesMacro() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOff(commands::add, 1_000L));
		assertEquals(1, commands.size());

		controller.onChatMessage("NebulaClient » Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOff(commands::add, 1_100L));
		assertEquals(2, commands.size());

		controller.onChatMessage("NebulaClient » Combat Macro: Disabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOff(commands::add, 1_200L));
		assertEquals(2, commands.size());
	}

	@Test
	void ensureOffFromUnknownCompletesWhenFirstToggleDisablesMacro() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOff(commands::add, 1_000L));
		controller.onChatMessage("NebulaClient » Combat Macro: Disabled");

		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOff(commands::add, 1_100L));
		assertEquals(1, commands.size());
	}

	@Test
	void pendingEnsureFailsAfterTimeout() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOn(commands::add, 1_000L));
		assertEquals(NebulaMacroController.EnsureResult.FAILED, controller.ensureOn(commands::add, 9_001L));
		assertEquals(1, commands.size());
	}

	@Test
	void autoRestoresWhenDesiredOnAndDisabledIsObserved() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertTrue(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);

		assertEquals(NebulaMacroController.AutoRestoreResult.PENDING, controller.autoRestoreIfDisabled(commands::add, 2_100L));
		assertEquals(1, commands.size());

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 2_200L));
		assertEquals(1, commands.size());
	}

	@Test
	void completedEnsureOnDoesNotBlockLaterAutoRestore() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOn(commands::add, 1_000L));
		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");

		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 1_500L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND, NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void programmaticStartClearsPreviousManualDisableSuppression() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertEquals(NebulaMacroController.ManualToggleIntentResult.DISABLING, controller.recordManualToggleIntent(1_500L));

		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.requestProgrammaticOn(commands::add, 1_600L));
		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");

		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND, NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void observedEnabledAloneArmsAutoRestore() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertTrue(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void manualToggleIntentSuppressesAutoRestore() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertEquals(NebulaMacroController.ManualToggleIntentResult.DISABLING, controller.recordManualToggleIntent(1_500L));
		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");

		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(), commands);
		assertFalse(controller.desiredOn());
	}

	@Test
	void manualToggleImmediatelyFlipsPredictedState() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));

		assertEquals(NebulaMacroController.ManualToggleIntentResult.DISABLING, controller.recordManualToggleIntent(1_500L));
		assertEquals(NebulaMacroController.ObservedState.OFF, controller.observedState());
		assertFalse(controller.desiredOn());
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 1_600L));
		assertEquals(List.of(), commands);
	}

	@Test
	void predictedManualEnableSuppressesStaleDisabledConfirmationUntilEnabledArrives() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertEquals(NebulaMacroController.ManualToggleIntentResult.DISABLING, controller.recordManualToggleIntent(1_500L));
		assertEquals(NebulaMacroController.ManualToggleIntentResult.ENABLING, controller.recordManualToggleIntent(1_700L));
		assertEquals(NebulaMacroController.ObservedState.ON, controller.observedState());
		assertTrue(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 1_800L));
		assertEquals(List.of(), commands);

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 1_900L));
		assertEquals(NebulaMacroController.ObservedState.ON, controller.observedState());
		assertTrue(controller.desiredOn());
		assertEquals(List.of(), commands);
	}

	@Test
	void manualToggleResultUsesAlreadyObservedDisabledStateAsDisableIntent() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");

		assertEquals(
			NebulaMacroController.ManualToggleIntentResult.DISABLING,
			controller.recordManualToggleResult(NebulaMacroController.ObservedState.OFF, 1_100L)
		);
		assertEquals(NebulaMacroController.ObservedState.OFF, controller.observedState());
		assertFalse(controller.desiredOn());
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 1_200L));
		assertEquals(List.of(), commands);
	}

	@Test
	void manualToggleResultUsesAlreadyObservedEnabledStateAsEnableIntent() {
		NebulaMacroController controller = new NebulaMacroController();

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");

		assertEquals(
			NebulaMacroController.ManualToggleIntentResult.ENABLING,
			controller.recordManualToggleResult(NebulaMacroController.ObservedState.ON, 1_100L)
		);
		assertEquals(NebulaMacroController.ObservedState.ON, controller.observedState());
		assertTrue(controller.desiredOn());
	}

	@Test
	void manualDisableIntentSuppressesAutoRestoreFromAnyObservedState() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		controller.recordManualDisableIntent();
		assertFalse(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertFalse(controller.desiredOn());
		controller.recordManualDisableIntent();
		assertFalse(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(), commands);
	}

	@Test
	void nebulaGuiDisabledMessageClearsDesiredOnAndSuppressesAutoRestore() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertTrue(controller.desiredOn());

		controller.onGuiChatMessage("NebulaClient > Combat Macro: Disabled");

		assertFalse(controller.desiredOn());
		assertEquals(NebulaMacroController.ObservedState.OFF, controller.observedState());
		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(), commands);
	}

	@Test
	void manualToggleOnKeepsAutoRestoreEnabled() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.ManualToggleIntentResult.ENABLING, controller.recordManualToggleIntent(1_000L));

		assertEquals(NebulaMacroController.AutoRestoreResult.IDLE, controller.autoRestoreIfDisabled(commands::add, 1_100L));
		assertEquals(List.of(), commands);

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertTrue(controller.desiredOn());

		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void unknownManualToggleWaitsForObservedResult() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		assertEquals(NebulaMacroController.ManualToggleIntentResult.UNKNOWN, controller.recordManualToggleIntent(1_000L));
		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");

		assertTrue(controller.desiredOn());
		controller.onChatMessage("NebulaClient > Combat Macro: Disabled");
		assertEquals(NebulaMacroController.AutoRestoreResult.STARTED, controller.autoRestoreIfDisabled(commands::add, 2_000L));
		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void ensureOffClearsDesiredOn() {
		NebulaMacroController controller = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.onChatMessage("NebulaClient > Combat Macro: Enabled");
		assertEquals(NebulaMacroController.EnsureResult.COMPLETE, controller.ensureOn(commands::add, 1_000L));
		assertEquals(NebulaMacroController.EnsureResult.PENDING, controller.ensureOff(commands::add, 2_000L));

		assertFalse(controller.desiredOn());
	}

	@Test
	void recognizesNebulaToggleCommand() {
		assertTrue(NebulaMacroController.isToggleCommand("/n toggleMacro combat macro"));
		assertTrue(NebulaMacroController.isToggleCommand("n   togglemacro   combat   macro"));
		assertFalse(NebulaMacroController.isToggleCommand("/n other"));
	}
}
