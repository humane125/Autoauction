package com.autoauction.client.lobby;

import com.autoauction.client.debug.SkyBlockStatus;
import com.autoauction.client.macro.NebulaMacroController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyCollisionControllerTest {
	@Test
	void doesNotSwitchOutsideTheEnd() {
		LobbyCollisionController controller = new LobbyCollisionController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();
		controller.updateRegisteredAccounts(List.of("FriendEndAlt"));

		controller.tick(snapshot("CurrentAlt", "Hub", List.of("FriendEndAlt"), true, 1_000L), macro, commands::add);

		assertEquals(List.of(), commands);
	}

	@Test
	void ignoresCurrentAccountAndUnregisteredNames() {
		LobbyCollisionController controller = new LobbyCollisionController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();
		controller.updateRegisteredAccounts(List.of("CurrentAlt", "OtherRegisteredAlt"));

		controller.tick(snapshot("CurrentAlt", "Hub", List.of("OtherRegisteredAlt"), true, 1_000L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("[MVP+] CurrentAlt", "RandomEndPlayer"), true, 1_100L), macro, commands::add);

		assertEquals(List.of(), commands);
	}

	@Test
	void switchesWhenNewlyEnteringTheEndWithRegisteredAccountVisible() {
		LobbyCollisionController controller = new LobbyCollisionController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();
		controller.updateRegisteredAccounts(List.of("FriendEndAlt"));

		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 1_000L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("[VIP] FriendEndAlt", "CurrentAlt"), true, 1_100L), macro, commands::add);
		macro.onChatMessage("NebulaClient > Combat Macro: Disabled");
		controller.tick(snapshot("CurrentAlt", "The End", List.of("FriendEndAlt", "CurrentAlt"), true, 1_200L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("FriendEndAlt", "CurrentAlt"), true, 2_500L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 2_700L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 7_699L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 7_700L), macro, commands::add);

		assertEquals(List.of(
			NebulaMacroController.TOGGLE_COMMAND,
			"/is",
			NebulaMacroController.TOGGLE_COMMAND
		), commands);
		assertEquals(1, controller.collisionSwitchCount());
	}

	@Test
	void waitsBeforeSwitchingAgainAfterItsOwnMacroRestartReturnsToAnotherOccupiedEndLobby() {
		LobbyCollisionController controller = new LobbyCollisionController();
		NebulaMacroController macro = enabledMacro();
		List<String> commands = new ArrayList<>();
		controller.updateRegisteredAccounts(List.of("FriendEndAlt", "SecondFriendAlt"));

		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 1_000L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("FriendEndAlt", "CurrentAlt"), true, 1_100L), macro, commands::add);
		macro.onChatMessage("NebulaClient > Combat Macro: Disabled");
		controller.tick(snapshot("CurrentAlt", "The End", List.of("FriendEndAlt", "CurrentAlt"), true, 1_200L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 2_700L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "Private Island", List.of("CurrentAlt"), true, 7_700L), macro, commands::add);
		macro.onChatMessage("NebulaClient > Combat Macro: Enabled");
		controller.tick(snapshot("CurrentAlt", "The End", List.of("SecondFriendAlt", "CurrentAlt"), true, 15_699L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("SecondFriendAlt", "CurrentAlt"), true, 15_700L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("SecondFriendAlt", "CurrentAlt"), true, 23_698L), macro, commands::add);
		controller.tick(snapshot("CurrentAlt", "The End", List.of("SecondFriendAlt", "CurrentAlt"), true, 23_699L), macro, commands::add);

		assertEquals(List.of(
			NebulaMacroController.TOGGLE_COMMAND,
			"/is",
			NebulaMacroController.TOGGLE_COMMAND,
			NebulaMacroController.TOGGLE_COMMAND
		), commands);
		assertEquals(2, controller.collisionSwitchCount());
	}

	private static NebulaMacroController enabledMacro() {
		NebulaMacroController macro = new NebulaMacroController();
		macro.onChatMessage("NebulaClient > Combat Macro: Enabled");
		macro.ensureOn(command -> {}, 1L);
		return macro;
	}

	private static LobbyCollisionController.Snapshot snapshot(
		String currentUsername,
		String area,
		List<String> tabListLines,
		boolean playerAvailable,
		long nowMs
	) {
		return new LobbyCollisionController.Snapshot(
			currentUsername,
			new SkyBlockStatus(Optional.of(area), Optional.empty(), OptionalLong.empty()),
			tabListLines,
			playerAvailable,
			nowMs
		);
	}
}
