package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenderParkingTrackerTest {
	@Test
	void parkedStacksAreScopedToMinecraftUsername() {
		SenderParkingTracker tracker = new SenderParkingTracker();

		tracker.recordParkedStack("OldSender", "ENCHANTED DIAMOND");

		assertEquals(1, tracker.parkedStacks("OldSender", "ENCHANTED DIAMOND"));
		assertEquals(0, tracker.parkedStacks("NewSender", "ENCHANTED DIAMOND"));
		assertFalse(tracker.restoreRequiredBeforeRunning("NewSender", "ENCHANTED DIAMOND"));
		assertTrue(tracker.restoreRequest("OldSender").isPresent());
		assertTrue(tracker.restoreRequest("NewSender").isEmpty());
	}

	@Test
	void differentItemRequiresRestoreOnlyForSameUsername() {
		SenderParkingTracker tracker = new SenderParkingTracker();

		tracker.recordParkedStack("OldSender", "ENCHANTED DIAMOND");

		assertTrue(tracker.restoreRequiredBeforeRunning("OldSender", "BLAZE ROD DISTILLATE"));
		assertFalse(tracker.restoreRequiredBeforeRunning("NewSender", "BLAZE ROD DISTILLATE"));
	}

	@Test
	void restoredStacksClearOnlyThatUsername() {
		SenderParkingTracker tracker = new SenderParkingTracker();
		tracker.recordParkedStack("OldSender", "ENCHANTED DIAMOND");
		tracker.recordParkedStack("NewSender", "BLAZE ROD DISTILLATE");

		tracker.recordRestoredStack("OldSender", "ENCHANTED DIAMOND");

		assertTrue(tracker.restoreRequest("OldSender").isEmpty());
		assertTrue(tracker.restoreRequest("NewSender").isPresent());
		assertEquals(1, tracker.parkedStacks("NewSender", "BLAZE ROD DISTILLATE"));
	}
}
