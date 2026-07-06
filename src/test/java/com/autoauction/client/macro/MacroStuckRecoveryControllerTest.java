package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MacroStuckRecoveryControllerTest {
	@Test
	void sendsHubBeforeRestartWhenStationaryInSkyBlock() {
		MacroStuckRecoveryController controller = new MacroStuckRecoveryController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();
		List<String> logs = new ArrayList<>();

		controller.tick(snapshot(0, true, 1_000L), macro, commands::add, logs::add);
		controller.tick(snapshot(0, true, 20_999L), macro, commands::add, logs::add);
		assertEquals(List.of(), commands);

		controller.tick(snapshot(0, true, 21_000L), macro, commands::add, logs::add);
		assertEquals(List.of("/hub"), commands);

		controller.tick(snapshot(0, true, 25_999L), macro, commands::add, logs::add);
		assertEquals(List.of("/hub"), commands);

		controller.tick(snapshot(0, true, 26_000L), macro, commands::add, logs::add);
		assertEquals(List.of("/hub", NebulaMacroController.TOGGLE_COMMAND), commands);

		macro.onChatMessage("NebulaClient > Combat Macro: Disabled");
		controller.tick(snapshot(0, true, 26_100L), macro, commands::add, logs::add);
		assertEquals(List.of("/hub", NebulaMacroController.TOGGLE_COMMAND, NebulaMacroController.TOGGLE_COMMAND), commands);

		macro.onChatMessage("NebulaClient > Combat Macro: Enabled");
		controller.tick(snapshot(0, true, 26_200L), macro, commands::add, logs::add);
		assertEquals("Macro stuck recovery completed; Nebula macro rearmed.", logs.get(logs.size() - 1));
	}

	@Test
	void restartsWithoutHubWhenStationaryOutsideSkyBlock() {
		MacroStuckRecoveryController controller = new MacroStuckRecoveryController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();

		controller.tick(snapshot(0, false, 1_000L), macro, commands::add, message -> {});
		controller.tick(snapshot(0, false, 21_000L), macro, commands::add, message -> {});

		assertEquals(List.of(NebulaMacroController.TOGGLE_COMMAND), commands);
	}

	@Test
	void movementResetsStationaryTimer() {
		MacroStuckRecoveryController controller = new MacroStuckRecoveryController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();

		controller.tick(snapshot(0, true, 1_000L), macro, commands::add, message -> {});
		controller.tick(snapshot(1, true, 20_500L), macro, commands::add, message -> {});
		controller.tick(snapshot(1, true, 21_000L), macro, commands::add, message -> {});

		assertEquals(List.of(), commands);
	}

	@Test
	void stationaryTimeBeforeMacroIsOnDoesNotTriggerImmediateRecovery() {
		MacroStuckRecoveryController controller = new MacroStuckRecoveryController();
		NebulaMacroController macro = new NebulaMacroController();
		List<String> commands = new ArrayList<>();

		controller.tick(snapshot(0, true, 1_000L), macro, commands::add, message -> {});
		macro.onChatMessage("NebulaClient > Combat Macro: Enabled");
		controller.tick(snapshot(0, true, 25_000L), macro, commands::add, message -> {});

		assertEquals(List.of(), commands);
	}

	@Test
	void manualStopCancelsPendingHubRecovery() {
		MacroStuckRecoveryController controller = new MacroStuckRecoveryController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();

		controller.tick(snapshot(0, true, 1_000L), macro, commands::add, message -> {});
		controller.tick(snapshot(0, true, 21_000L), macro, commands::add, message -> {});
		macro.recordManualDisableIntent();
		controller.tick(snapshot(0, true, 26_000L), macro, commands::add, message -> {});

		assertEquals(List.of("/hub"), commands);
	}

	private static NebulaMacroController enabledMacro() {
		NebulaMacroController macro = new NebulaMacroController();
		macro.onChatMessage("NebulaClient > Combat Macro: Enabled");
		return macro;
	}

	private static MacroStuckRecoveryController.Snapshot snapshot(double x, boolean inSkyBlock, long nowMs) {
		return new MacroStuckRecoveryController.Snapshot(
			new MacroStuckRecoveryController.Position(x, 64.0, 0.0),
			true,
			inSkyBlock,
			nowMs
		);
	}
}
