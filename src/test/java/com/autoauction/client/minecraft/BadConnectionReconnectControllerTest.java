package com.autoauction.client.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BadConnectionReconnectControllerTest {
	@Test
	void schedulesBadConnectionDisconnectsWithProvidedDelay() {
		BadConnectionReconnectController controller = new BadConnectionReconnectController();

		boolean scheduled = controller.scheduleIfBadConnection(
			"Internal Exception: java.net.SocketException: Connection reset",
			false,
			1_000L,
			() -> 12_345L
		);

		assertEquals(true, scheduled);
		assertEquals(true, controller.hasPendingReconnect());
		assertEquals(13_345L, controller.pendingReconnectAt());
	}

	@Test
	void doesNotScheduleBanDisconnects() {
		BadConnectionReconnectController controller = new BadConnectionReconnectController();

		boolean scheduled = controller.scheduleIfBadConnection(
			"You are permanently banned from this server! Ban ID: #123",
			true,
			1_000L,
			() -> 10_000L
		);

		assertEquals(false, scheduled);
		assertEquals(false, controller.hasPendingReconnect());
	}

	@Test
	void intentionalDisconnectSuppressionBlocksBadConnectionReconnect() {
		BadConnectionReconnectController controller = new BadConnectionReconnectController();

		controller.suppressIntentionalDisconnects(1_000L);
		boolean scheduled = controller.scheduleIfBadConnection("Timed out", false, 20_000L, () -> 10_000L);

		assertEquals(false, scheduled);
		assertEquals(false, controller.hasPendingReconnect());
	}

	@Test
	void manualReconnectClearsPendingCountdown() {
		BadConnectionReconnectController controller = new BadConnectionReconnectController();

		controller.scheduleIfBadConnection("Connection lost: Timed out", false, 1_000L, () -> 10_000L);
		controller.clearPendingReconnect();

		assertEquals(false, controller.hasPendingReconnect());
		assertEquals(0L, controller.pendingReconnectAt());
	}

	@Test
	void badConnectionReasonDetectionIsSpecific() {
		assertEquals(true, BadConnectionReconnectController.isBadConnectionKick("Timed out"));
		assertEquals(true, BadConnectionReconnectController.isBadConnectionKick("Connection reset"));
		assertEquals(true, BadConnectionReconnectController.isBadConnectionKick("An existing connection was forcibly closed by the remote host"));
		assertEquals(true, BadConnectionReconnectController.isBadConnectionKick("Internal Exception: java.net.SocketException"));
		assertEquals(false, BadConnectionReconnectController.isBadConnectionKick("Disconnected by user"));
		assertEquals(false, BadConnectionReconnectController.isBadConnectionKick("You are permanently banned from this server!"));
	}
}
