package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
