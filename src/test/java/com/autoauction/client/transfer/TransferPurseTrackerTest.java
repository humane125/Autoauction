package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferPurseTrackerTest {
	@Test
	void recordsBeforeAfterAndDelta() {
		TransferPurseTracker tracker = new TransferPurseTracker();

		tracker.start("Receiver", OptionalLong.of(1_000_000L));
		TransferPurseTracker.Summary summary = tracker.finish(OptionalLong.of(1_875_543L)).orElseThrow();

		assertEquals("Receiver", summary.role());
		assertEquals(1_000_000L, summary.before());
		assertEquals(1_875_543L, summary.after());
		assertEquals(875_543L, summary.delta());
		assertFalse(tracker.finish(OptionalLong.of(2_000_000L)).isPresent());
	}

	@Test
	void skipsSummaryWhenPurseIsMissing() {
		TransferPurseTracker tracker = new TransferPurseTracker();

		tracker.start("Sender", OptionalLong.empty());

		assertTrue(tracker.finish(OptionalLong.of(10_000L)).isEmpty());
	}
}
