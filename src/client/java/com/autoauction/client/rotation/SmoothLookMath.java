package com.autoauction.client.rotation;

public final class SmoothLookMath {
	private SmoothLookMath() {
	}

	public static Angles anglesTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
		double xDiff = toX - fromX;
		double yDiff = toY - fromY;
		double zDiff = toZ - fromZ;
		double horizontalDistance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
		float yaw = (float) Math.toDegrees(-Math.atan2(xDiff, zDiff));
		float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, horizontalDistance));
		return new Angles(yaw, clampPitch(pitch));
	}

	public static Angles nextAngles(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float progress) {
		float eased = smoothstep(progress);
		float yawDelta = wrapDegrees(targetYaw - currentYaw);
		float pitchDelta = clampPitch(targetPitch) - currentPitch;
		return new Angles(
			currentYaw + yawDelta * eased,
			clampPitch(currentPitch + pitchDelta * eased)
		);
	}

	public static boolean closeEnough(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float thresholdDegrees) {
		return Math.abs(wrapDegrees(targetYaw - currentYaw)) <= thresholdDegrees
			&& Math.abs(clampPitch(targetPitch) - currentPitch) <= thresholdDegrees;
	}

	public static float wrapDegrees(float value) {
		float wrapped = value % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		}
		if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	public static float smoothstep(float value) {
		float t = Math.max(0.0f, Math.min(1.0f, value));
		return t * t * (3.0f - 2.0f * t);
	}

	public static float clampPitch(float pitch) {
		return Math.max(-90.0f, Math.min(90.0f, pitch));
	}

	public record Angles(float yaw, float pitch) {
	}
}
