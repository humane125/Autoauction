package com.autoauction.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Random;

/**
 * Adapted from Halo's TimeBasedRotation system for AutoAuction.
 *
 * Halo's original class used a global Minecraft singleton plus Halo chat/debug
 * utilities. This version keeps the same time-based one-shot and continuous
 * rotation model, but accepts the Minecraft client from AutoAuction's render
 * hook and exposes small pure helpers for tests.
 */
public final class TimeBasedRotation {
	private static final float PITCH_LAG_FACTOR = 0.85f;
	private static final float CORRECTION_THRESHOLD_DEG = 3.5f;
	private static final float NOISE_INTERVAL_SEC = 0.028f;
	private static final float RAMP_UP_MS = 180.0f;
	private static final float TARGET_SMOOTH_RATE = 14.0f;
	private static final float TARGET_UPDATE_DEADZONE_DEG = 0.08f;
	private static final float TARGET_JUMP_THRESHOLD_DEG = 25.0f;
	private static final int HISTORY_SIZE = 8;
	private static final float MOMENTUM_BLEND = 0.2f;

	private float degreesPerSecond = 600.0f;
	private float minDurationMs = 20.0f;
	private float maxDurationMs = 800.0f;
	private float jitterAmplitude = 0.06f;

	private float startYaw;
	private float startPitch;
	private float targetYaw;
	private float targetPitch;
	private float totalYawDist;
	private float totalPitchDist;
	private long startTimeNs;
	private float durationMs;
	private boolean active;
	private boolean complete;
	private Runnable completionHandler;

	private boolean continuousMode;
	private long lastTickNs;
	private long continuousStartNs;
	private float continuousRateScale = 1.0f;
	private float continuousAccelerationScale = 1.0f;
	private float continuousJerkScale = 1.0f;
	private float continuousMomentumScale = 1.0f;
	private float continuousProximityFloor = 0.15f;
	private float continuousRampUpScale = 1.0f;

	private float rawTargetYaw = Float.NaN;
	private float rawTargetPitch = Float.NaN;
	private long lastTargetUpdateNs;
	private float accelerationYaw;
	private float accelerationPitch;
	private float velocityYaw;
	private float velocityPitch;
	private float smoothedYaw = Float.NaN;
	private float smoothedPitch = Float.NaN;

	private final Random humanRng = new Random();
	private float correctionPhase;
	private float correctionAmplitude;
	private float noiseYaw;
	private float noisePitch;
	private float noiseTimer;
	private float breathPhase;
	private float driftOffset;
	private float driftDirection;
	private float oneShotDriftDir;
	private float oneShotNoisePhase;
	private final float[] historyYawVel = new float[HISTORY_SIZE];
	private final float[] historyPitchVel = new float[HISTORY_SIZE];
	private int historyIndex;

	public TimeBasedRotation setSpeed(float degreesPerSecond) {
		this.degreesPerSecond = Math.max(10.0f, degreesPerSecond);
		return this;
	}

	public TimeBasedRotation setMinDuration(float minMs) {
		this.minDurationMs = Math.max(1.0f, minMs);
		return this;
	}

	public TimeBasedRotation setMaxDuration(float maxMs) {
		this.maxDurationMs = Math.max(minDurationMs + 1.0f, maxMs);
		return this;
	}

	public TimeBasedRotation setJitter(float amplitude) {
		this.jitterAmplitude = Math.max(0.0f, amplitude);
		return this;
	}

	public TimeBasedRotation setContinuousRateScale(float scale) {
		this.continuousRateScale = Math.max(0.1f, scale);
		return this;
	}

	public TimeBasedRotation setContinuousAccelerationScale(float scale) {
		this.continuousAccelerationScale = Math.max(0.05f, scale);
		return this;
	}

	public TimeBasedRotation setContinuousJerkScale(float scale) {
		this.continuousJerkScale = Math.max(0.05f, scale);
		return this;
	}

	public TimeBasedRotation setContinuousMomentumScale(float scale) {
		this.continuousMomentumScale = Math.max(0.0f, scale);
		return this;
	}

	public TimeBasedRotation setContinuousProximityFloor(float floor) {
		this.continuousProximityFloor = clamp(floor, 0.01f, 1.0f);
		return this;
	}

	public TimeBasedRotation setContinuousRampUpScale(float scale) {
		this.continuousRampUpScale = Math.max(0.25f, scale);
		return this;
	}

	public void startRotation(Minecraft client, float targetYaw, float targetPitch) {
		startRotation(client, targetYaw, targetPitch, null);
	}

	public void startRotation(Minecraft client, float targetYaw, float targetPitch, Runnable completionHandler) {
		if (client.player == null) {
			return;
		}
		this.startYaw = client.player.getYRot();
		this.startPitch = client.player.getXRot();
		this.targetYaw = targetYaw;
		this.targetPitch = clampPitch(targetPitch);
		this.totalYawDist = wrappedYawDistance(startYaw, targetYaw);
		this.totalPitchDist = this.targetPitch - startPitch;
		this.durationMs = calculateDuration(totalYawDist, totalPitchDist);
		this.startTimeNs = System.nanoTime();
		this.completionHandler = completionHandler;
		this.active = true;
		this.complete = false;
		this.continuousMode = false;
		this.oneShotDriftDir = (humanRng.nextFloat() - 0.5f) * 2.0f;
		this.oneShotNoisePhase = humanRng.nextFloat() * (float) (Math.PI * 2.0);
	}

	public void startRotationTo(Minecraft client, Vec3 worldPos) {
		startRotationTo(client, worldPos, null);
	}

	public void startRotationTo(Minecraft client, Vec3 worldPos, Runnable completionHandler) {
		if (client.player == null || worldPos == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		Angles angles = getRotationFromPosition(eye.x, eye.y, eye.z, worldPos.x, worldPos.y, worldPos.z);
		startRotation(client, angles.yaw(), angles.pitch(), completionHandler);
	}

	public void enableContinuous(Minecraft client) {
		long nowNs = System.nanoTime();
		this.continuousMode = true;
		this.lastTickNs = nowNs;
		this.continuousStartNs = nowNs;
		this.active = true;
		this.complete = false;
		if (client.player != null) {
			this.smoothedYaw = client.player.getYRot();
			this.smoothedPitch = client.player.getXRot();
		} else {
			this.smoothedYaw = Float.NaN;
			this.smoothedPitch = Float.NaN;
		}
		this.velocityYaw = 0.0f;
		this.velocityPitch = 0.0f;
		this.accelerationYaw = 0.0f;
		this.accelerationPitch = 0.0f;
		this.correctionPhase = humanRng.nextFloat() * (float) (Math.PI * 2.0);
		this.correctionAmplitude = 0.0f;
		this.breathPhase = humanRng.nextFloat() * (float) (Math.PI * 2.0);
		this.driftDirection = (humanRng.nextFloat() - 0.5f) * 2.0f;
		this.driftOffset = 0.0f;
		this.noiseYaw = 0.0f;
		this.noisePitch = 0.0f;
		this.noiseTimer = 0.0f;
		this.rawTargetYaw = Float.NaN;
		this.rawTargetPitch = Float.NaN;
		this.lastTargetUpdateNs = 0L;
	}

	public void updateTarget(float newTargetYaw, float newTargetPitch) {
		newTargetPitch = clampPitch(newTargetPitch);
		if (!continuousMode) {
			this.targetYaw = newTargetYaw;
			this.targetPitch = newTargetPitch;
			return;
		}

		long nowNs = System.nanoTime();
		if (Float.isNaN(rawTargetYaw)) {
			this.rawTargetYaw = newTargetYaw;
			this.rawTargetPitch = newTargetPitch;
			this.targetYaw = newTargetYaw;
			this.targetPitch = newTargetPitch;
			this.lastTargetUpdateNs = nowNs;
			return;
		}

		float deltaYaw = wrappedYawDistance(rawTargetYaw, newTargetYaw);
		float deltaPitch = newTargetPitch - rawTargetPitch;
		float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
		if (totalDelta < TARGET_UPDATE_DEADZONE_DEG) {
			return;
		}
		if (totalDelta > TARGET_JUMP_THRESHOLD_DEG) {
			this.rawTargetYaw = newTargetYaw;
			this.rawTargetPitch = newTargetPitch;
			this.targetYaw = newTargetYaw;
			this.targetPitch = newTargetPitch;
			this.lastTargetUpdateNs = nowNs;
			return;
		}

		float dtSec = (nowNs - lastTargetUpdateNs) / 1_000_000_000.0f;
		dtSec = clamp(dtSec, 0.001f, 0.5f);
		this.rawTargetYaw = newTargetYaw;
		this.rawTargetPitch = newTargetPitch;
		this.lastTargetUpdateNs = nowNs;

		float smoothAlpha = 1.0f - (float) Math.exp(-TARGET_SMOOTH_RATE * dtSec);
		this.targetYaw += wrappedYawDistance(targetYaw, newTargetYaw) * smoothAlpha;
		this.targetPitch = clampPitch(targetPitch + (newTargetPitch - targetPitch) * smoothAlpha);
	}

	public void updateTargetPos(Minecraft client, Vec3 worldPos) {
		if (client.player == null || worldPos == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		Angles angles = getRotationFromPosition(eye.x, eye.y, eye.z, worldPos.x, worldPos.y, worldPos.z);
		updateTarget(angles.yaw(), angles.pitch());
	}

	public void tick(Minecraft client) {
		if (!active || client.player == null) {
			return;
		}
		Angles next = continuousMode ? tickContinuous(client) : tickOneShot(client);
		if (next == null) {
			return;
		}
		applyRotation(client, next.yaw(), next.pitch());
		if (!active && completionHandler != null) {
			Runnable handler = completionHandler;
			completionHandler = null;
			handler.run();
		}
	}

	private Angles tickOneShot(Minecraft client) {
		long nowNs = System.nanoTime();
		float elapsedMs = (nowNs - startTimeNs) / 1_000_000.0f;
		float t = Math.min(1.0f, elapsedMs / durationMs);
		float easedYaw = smoothstep(t);
		float delayedT = t * PITCH_LAG_FACTOR + (1.0f - PITCH_LAG_FACTOR) * t * t;
		float easedPitch = smoothstep(Math.min(1.0f, delayedT));

		float newYaw = startYaw + totalYawDist * easedYaw;
		float newPitch = startPitch + totalPitchDist * easedPitch;
		float totalDeg = (float) Math.sqrt(totalYawDist * totalYawDist + totalPitchDist * totalPitchDist);

		if (totalDeg > 5.0f && jitterAmplitude > 0.0f) {
			float driftMag = Math.min(1.5f, totalDeg * 0.012f) * oneShotDriftDir;
			float bellCurve = 4.0f * t * (1.0f - t);
			float yawWeight = Math.abs(totalYawDist) / (Math.abs(totalYawDist) + Math.abs(totalPitchDist) + 0.001f);
			newPitch += driftMag * bellCurve * yawWeight;
			newYaw += driftMag * bellCurve * (1.0f - yawWeight) * 0.6f;
		}

		if (jitterAmplitude > 0.0f && t > 0.05f && t < 0.92f) {
			float noiseT = oneShotNoisePhase + t * 14.0f;
			float noise = (float) (Math.sin(noiseT * 2.73) * 0.28 + Math.sin(noiseT * 4.17) * 0.14);
			float noiseMag = jitterAmplitude * 1.2f;
			newYaw += noise * noiseMag;
			newPitch += noise * noiseMag * 0.4f;
		}

		Angles snapped = gcdSnap(client, client.player.getYRot(), client.player.getXRot(), newYaw, clampPitch(newPitch));
		if (t >= 1.0f) {
			complete = true;
			active = false;
		}
		return snapped;
	}

	private Angles tickContinuous(Minecraft client) {
		long nowNs = System.nanoTime();
		float dtSec = (nowNs - lastTickNs) / 1_000_000_000.0f;
		lastTickNs = nowNs;
		dtSec = clamp(dtSec, 0.0001f, 0.1f);

		float currentYaw = client.player.getYRot();
		float currentPitch = client.player.getXRot();
		if (Float.isNaN(smoothedYaw)) {
			smoothedYaw = currentYaw;
		}
		if (Float.isNaN(smoothedPitch)) {
			smoothedPitch = currentPitch;
		}

		float deltaYaw = wrappedYawDistance(smoothedYaw, targetYaw);
		float deltaPitch = targetPitch - smoothedPitch;
		float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
		float rate = clamp((degreesPerSecond / 40.0f) * continuousRateScale, 2.0f, 50.0f);
		float alpha = 1.0f - (float) Math.exp(-rate * dtSec);

		float elapsedSinceStartMs = (nowNs - continuousStartNs) / 1_000_000.0f;
		float rampDurationMs = RAMP_UP_MS * continuousRampUpScale;
		if (elapsedSinceStartMs < rampDurationMs) {
			float rampT = elapsedSinceStartMs / rampDurationMs;
			alpha *= smoothstep(rampT);
		}

		float alphaYaw = alpha;
		float alphaPitch = Math.abs(deltaPitch) > Math.abs(deltaYaw) * 2.0f ? alpha * 0.92f : alpha * PITCH_LAG_FACTOR;
		float settledThreshold = 1.5f;
		boolean isSettled = totalDelta < settledThreshold;
		float settledFactor = isSettled ? totalDelta / settledThreshold : 1.0f;
		settledFactor *= settledFactor;

		float maxAcceleration = degreesPerSecond * 1.5f * continuousAccelerationScale;
		float maxJerk = maxAcceleration * 8.0f * continuousJerkScale;
		float proximityFactor = Math.max(continuousProximityFloor, Math.min(1.0f, totalDelta / 15.0f));
		float maxDegreesThisFrame = degreesPerSecond * dtSec * 1.2f * proximityFactor;
		float desiredVelYaw = deltaYaw * alphaYaw / dtSec;
		float desiredVelPitch = deltaPitch * alphaPitch / dtSec;

		float momYaw = 0.0f;
		float momPitch = 0.0f;
		for (int i = 0; i < HISTORY_SIZE; i++) {
			momYaw += historyYawVel[i];
			momPitch += historyPitchVel[i];
		}
		momYaw /= HISTORY_SIZE;
		momPitch /= HISTORY_SIZE;
		float momentumBlend = clamp(MOMENTUM_BLEND * continuousMomentumScale * settledFactor, 0.0f, 0.95f);
		desiredVelYaw = desiredVelYaw * (1.0f - momentumBlend) + momYaw * momentumBlend;
		desiredVelPitch = desiredVelPitch * (1.0f - momentumBlend) + momPitch * momentumBlend;

		if (jitterAmplitude > 0.0f && settledFactor > 0.1f) {
			noiseTimer -= dtSec;
			if (noiseTimer <= 0.0f) {
				noiseTimer = NOISE_INTERVAL_SEC + humanRng.nextFloat() * 0.012f;
				float targetNoiseYaw = (humanRng.nextFloat() - 0.5f) * 0.16f;
				float targetNoisePitch = (humanRng.nextFloat() - 0.5f) * 0.10f;
				noiseYaw += (targetNoiseYaw - noiseYaw) * 0.6f;
				noisePitch += (targetNoisePitch - noisePitch) * 0.6f;
			}
			desiredVelYaw *= 1.0f + noiseYaw * settledFactor;
			desiredVelPitch *= 1.0f + noisePitch * settledFactor;
		}

		float desiredAccelYaw = (desiredVelYaw - velocityYaw) / dtSec;
		float desiredAccelPitch = (desiredVelPitch - velocityPitch) / dtSec;
		float maxAccelDelta = maxJerk * dtSec;
		accelerationYaw = clamp(clampDelta(accelerationYaw, desiredAccelYaw, maxAccelDelta), -maxAcceleration, maxAcceleration);
		accelerationPitch = clamp(clampDelta(accelerationPitch, desiredAccelPitch, maxAccelDelta), -maxAcceleration, maxAcceleration);
		velocityYaw = clampDelta(velocityYaw, desiredVelYaw, Math.abs(accelerationYaw) * dtSec);
		velocityPitch = clampDelta(velocityPitch, desiredVelPitch, Math.abs(accelerationPitch) * dtSec);

		if (isSettled) {
			for (int i = 0; i < HISTORY_SIZE; i++) {
				historyYawVel[i] *= 0.8f;
				historyPitchVel[i] *= 0.8f;
			}
		}
		historyYawVel[historyIndex] = velocityYaw;
		historyPitchVel[historyIndex] = velocityPitch;
		historyIndex = (historyIndex + 1) % HISTORY_SIZE;

		float stepYaw = clamp(velocityYaw * dtSec, -maxDegreesThisFrame, maxDegreesThisFrame);
		float stepPitch = clamp(velocityPitch * dtSec, -maxDegreesThisFrame, maxDegreesThisFrame);
		if (Math.abs(stepYaw) > Math.abs(deltaYaw)) {
			stepYaw = deltaYaw;
		}
		if (Math.abs(stepPitch) > Math.abs(deltaPitch)) {
			stepPitch = deltaPitch;
		}

		if (jitterAmplitude > 0.0f && totalDelta > 5.0f) {
			float speed = Math.abs(velocityYaw) + Math.abs(velocityPitch);
			driftOffset += speed * 0.0008f * driftDirection * dtSec;
			driftOffset *= 0.97f;
			float yawWeight = Math.abs(deltaYaw) / (totalDelta + 0.001f);
			stepPitch += driftOffset * yawWeight * dtSec;
			stepYaw += driftOffset * (1.0f - yawWeight) * 0.4f * dtSec;
		} else {
			driftOffset *= 0.9f;
		}

		smoothedYaw += stepYaw;
		smoothedPitch = clampPitch(smoothedPitch + stepPitch);
		float newYaw = smoothedYaw;
		float newPitch = smoothedPitch;

		if (jitterAmplitude > 0.0f) {
			if (totalDelta < CORRECTION_THRESHOLD_DEG && totalDelta > 0.08f) {
				float closeness = 1.0f - totalDelta / CORRECTION_THRESHOLD_DEG;
				correctionAmplitude += (closeness * 0.35f - correctionAmplitude) * dtSec * 5.0f;
				correctionPhase += dtSec * (8.5f + humanRng.nextFloat() * 2.5f);
				float osc = (float) Math.sin(correctionPhase) * correctionAmplitude * jitterAmplitude * 2.5f;
				newYaw += osc;
				newPitch += osc * 0.35f;
			} else {
				correctionAmplitude *= 1.0f - dtSec * 6.0f;
				if (correctionAmplitude < 0.001f) {
					correctionAmplitude = 0.0f;
				}
			}

			breathPhase += dtSec * (0.37f + 0.08f * (float) Math.sin(nowNs / 4_200_000_000.0));
			float bYaw = (float) (
				Math.sin(breathPhase * 2.0 * Math.PI) * 0.42
					+ Math.sin(breathPhase * 2.0 * Math.PI * 1.73 + 0.8) * 0.18
			) * jitterAmplitude;
			float bPitch = (float) (
				Math.sin(breathPhase * 2.0 * Math.PI * 0.71 + 1.3) * 0.28
					+ Math.sin(breathPhase * 2.0 * Math.PI * 1.41 + 2.1) * 0.12
			) * jitterAmplitude;
			newYaw += bYaw;
			newPitch += bPitch;
		}

		return gcdSnap(client, currentYaw, currentPitch, newYaw, clampPitch(newPitch));
	}

	private static float clampDelta(float current, float target, float maxChange) {
		float delta = target - current;
		if (Math.abs(delta) <= maxChange) {
			return target;
		}
		return current + Math.signum(delta) * maxChange;
	}

	private float calculateDuration(float yawDist, float pitchDist) {
		float totalDegrees = (float) Math.sqrt(yawDist * yawDist + pitchDist * pitchDist);
		float rawMs = totalDegrees / degreesPerSecond * 1000.0f;
		return clamp(rawMs, minDurationMs, maxDurationMs);
	}

	private static Angles gcdSnap(Minecraft client, float currentYaw, float currentPitch, float newYaw, float newPitch) {
		double sensitivity = client.options.sensitivity().get() * 0.6 + 0.2;
		double gcd = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
		float deltaYaw = newYaw - currentYaw;
		float deltaPitch = newPitch - currentPitch;
		if (Math.abs(deltaYaw) + Math.abs(deltaPitch) < 0.15f || gcd <= 0.0001) {
			return new Angles(newYaw, clampPitch(newPitch));
		}
		float snappedYaw = currentYaw + (float) (Math.round(deltaYaw / gcd) * gcd);
		float snappedPitch = currentPitch + (float) (Math.round(deltaPitch / gcd) * gcd);
		return new Angles(snappedYaw, clampPitch(snappedPitch));
	}

	private static void applyRotation(Minecraft client, float yaw, float pitch) {
		client.player.setYRot(yaw);
		client.player.setXRot(pitch);
		client.player.yRotO = yaw;
		client.player.xRotO = pitch;
	}

	public static Angles getRotationFromPosition(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
		double dx = toX - fromX;
		double dy = toY - fromY;
		double dz = toZ - fromZ;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
		return new Angles(yaw, clampPitch(pitch));
	}

	public static float wrappedYawDistance(float fromYaw, float toYaw) {
		return wrapDegrees(toYaw - fromYaw);
	}

	public static float smoothstep(float t) {
		float clamped = clamp(t, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	public static float clampPitch(float pitch) {
		return clamp(pitch, -90.0f, 90.0f);
	}

	private static float wrapDegrees(float value) {
		float wrapped = value % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		}
		if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public boolean isActive() {
		return active;
	}

	public boolean isComplete() {
		return complete;
	}

	public boolean isContinuous() {
		return continuousMode;
	}

	public float getTargetYaw() {
		return targetYaw;
	}

	public float getTargetPitch() {
		return targetPitch;
	}

	public float getProgress() {
		if (!active || continuousMode) {
			return complete ? 1.0f : 0.0f;
		}
		float elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000.0f;
		return Math.min(1.0f, elapsedMs / durationMs);
	}

	public float getRemainingDegrees(Minecraft client) {
		if (client.player == null) {
			return 0.0f;
		}
		float dYaw = Math.abs(wrappedYawDistance(client.player.getYRot(), targetYaw));
		float dPitch = Math.abs(targetPitch - client.player.getXRot());
		return dYaw + dPitch;
	}

	public void stop() {
		active = false;
		continuousMode = false;
		completionHandler = null;
		velocityYaw = 0.0f;
		velocityPitch = 0.0f;
		accelerationYaw = 0.0f;
		accelerationPitch = 0.0f;
		rawTargetYaw = Float.NaN;
		rawTargetPitch = Float.NaN;
		lastTargetUpdateNs = 0L;
	}

	public void reset() {
		stop();
		complete = false;
		lastTickNs = 0L;
		smoothedYaw = Float.NaN;
		smoothedPitch = Float.NaN;
		correctionPhase = 0.0f;
		correctionAmplitude = 0.0f;
		noiseYaw = 0.0f;
		noisePitch = 0.0f;
		noiseTimer = 0.0f;
		breathPhase = 0.0f;
		driftOffset = 0.0f;
		Arrays.fill(historyYawVel, 0.0f);
		Arrays.fill(historyPitchVel, 0.0f);
		historyIndex = 0;
		continuousRateScale = 1.0f;
		continuousAccelerationScale = 1.0f;
		continuousJerkScale = 1.0f;
		continuousMomentumScale = 1.0f;
		continuousProximityFloor = 0.15f;
		continuousRampUpScale = 1.0f;
	}

	public record Angles(float yaw, float pitch) {
	}
}
