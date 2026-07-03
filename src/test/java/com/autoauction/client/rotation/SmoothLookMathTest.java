package com.autoauction.client.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothLookMathTest {
	@Test
	void calculatesMinecraftYawPitchToWorldPosition() {
		SmoothLookMath.Angles south = SmoothLookMath.anglesTo(0, 0, 0, 0, 0, 10);
		SmoothLookMath.Angles east = SmoothLookMath.anglesTo(0, 0, 0, 10, 0, 0);
		SmoothLookMath.Angles above = SmoothLookMath.anglesTo(0, 0, 0, 0, 10, 10);

		assertEquals(0.0f, south.yaw(), 0.001f);
		assertEquals(0.0f, south.pitch(), 0.001f);
		assertEquals(-90.0f, east.yaw(), 0.001f);
		assertTrue(above.pitch() < 0.0f);
	}

	@Test
	void smoothsTowardWrappedYawWithoutTakingLongWayAround() {
		SmoothLookMath.Angles next = SmoothLookMath.nextAngles(170.0f, 0.0f, -170.0f, 0.0f, 0.5f);

		assertEquals(180.0f, next.yaw(), 0.001f);
		assertEquals(0.0f, next.pitch(), 0.001f);
	}

	@Test
	void detectsCloseEnoughAcrossYawWrap() {
		assertTrue(SmoothLookMath.closeEnough(179.5f, 2.0f, -179.8f, 2.4f, 1.0f));
	}
}
