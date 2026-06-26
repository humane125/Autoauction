package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NebulaGuiInputTrackerTest {
	@Test
	void tracksCustomGuiOpenUntilEscapeIsPressed() {
		NebulaGuiInputTracker tracker = new NebulaGuiInputTracker();

		assertTrue(tracker.tick(false, true, false));
		assertTrue(tracker.blocksMacroIntent(false));

		assertFalse(tracker.tick(false, false, false));
		assertTrue(tracker.blocksMacroIntent(false));

		assertFalse(tracker.tick(false, false, true));
		assertFalse(tracker.blocksMacroIntent(false));
	}

	@Test
	void ignoresRepeatedGuiKeyWhileHeld() {
		NebulaGuiInputTracker tracker = new NebulaGuiInputTracker();

		assertTrue(tracker.tick(false, true, false));
		assertFalse(tracker.tick(false, true, true));

		assertFalse(tracker.blocksMacroIntent(false));
	}

	@Test
	void blocksMacroIntentWhileMinecraftScreenIsOpen() {
		NebulaGuiInputTracker tracker = new NebulaGuiInputTracker();

		assertTrue(tracker.blocksMacroIntent(true));
		assertFalse(tracker.blocksMacroIntent(false));
	}

	@Test
	void ignoresGuiKeyPressWhileMinecraftScreenIsAlreadyOpen() {
		NebulaGuiInputTracker tracker = new NebulaGuiInputTracker();

		assertFalse(tracker.tick(true, false, false));
		assertFalse(tracker.tick(true, true, false));
		assertTrue(tracker.blocksMacroIntent(true));
		assertFalse(tracker.blocksMacroIntent(false));
	}

	@Test
	void tracksGuiKeyPressWhenScreenOpensOnSameTick() {
		NebulaGuiInputTracker tracker = new NebulaGuiInputTracker();

		assertFalse(tracker.tick(false, false, false));
		assertTrue(tracker.tick(true, true, false));
		assertTrue(tracker.blocksMacroIntent(false));
	}
}
