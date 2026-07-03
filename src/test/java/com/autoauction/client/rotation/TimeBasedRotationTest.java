package com.autoauction.client.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeBasedRotationTest {
	@Test
	void calculatesMinecraftYawPitchToWorldPosition() {
		TimeBasedRotation.Angles south = TimeBasedRotation.getRotationFromPosition(0, 0, 0, 0, 0, 10);
		TimeBasedRotation.Angles east = TimeBasedRotation.getRotationFromPosition(0, 0, 0, 10, 0, 0);
		TimeBasedRotation.Angles above = TimeBasedRotation.getRotationFromPosition(0, 0, 0, 0, 10, 10);

		assertEquals(0.0f, south.yaw(), 0.001f);
		assertEquals(0.0f, south.pitch(), 0.001f);
		assertEquals(-90.0f, east.yaw(), 0.001f);
		assertTrue(above.pitch() < 0.0f);
	}

	@Test
	void usesShortestYawPathAcrossWrap() {
		assertEquals(20.0f, TimeBasedRotation.wrappedYawDistance(170.0f, -170.0f), 0.001f);
		assertEquals(-20.0f, TimeBasedRotation.wrappedYawDistance(-170.0f, 170.0f), 0.001f);
	}

	@Test
	void smoothstepClampsToValidProgressRange() {
		assertEquals(0.0f, TimeBasedRotation.smoothstep(-1.0f), 0.001f);
		assertEquals(0.5f, TimeBasedRotation.smoothstep(0.5f), 0.001f);
		assertEquals(1.0f, TimeBasedRotation.smoothstep(2.0f), 0.001f);
	}
}
