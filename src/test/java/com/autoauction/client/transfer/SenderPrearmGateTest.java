package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenderPrearmGateTest {
	@Test
	void sellPrearmWaitsForReadyBeforeFinalClick() {
		SenderPrearmGate gate = new SenderPrearmGate();

		assertFalse(gate.canFinalClick());
		gate.markScreenArmed();
		assertFalse(gate.canFinalClick());
		gate.markReadySignal();

		assertTrue(gate.canFinalClick());
	}
}
