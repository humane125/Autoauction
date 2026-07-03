package com.autoauction.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class SmoothLookController {
	private static final long DEFAULT_DURATION_MS = 550L;
	private static final long SETTLE_DURATION_MS = 160L;

	private boolean active;
	private boolean settling;
	private float startYaw;
	private float startPitch;
	private float targetYaw;
	private float targetPitch;
	private long startedAtMs;
	private long settleUntilMs;
	private long durationMs = DEFAULT_DURATION_MS;
	private Runnable completionHandler;

	public void lookAt(Minecraft client, Vec3 target, Runnable completionHandler) {
		if (client.player == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		SmoothLookMath.Angles targetAngles = SmoothLookMath.anglesTo(eye.x, eye.y, eye.z, target.x, target.y, target.z);
		this.startYaw = client.player.getYRot();
		this.startPitch = client.player.getXRot();
		this.targetYaw = targetAngles.yaw();
		this.targetPitch = targetAngles.pitch();
		float distance = Math.abs(SmoothLookMath.wrapDegrees(targetYaw - startYaw)) + Math.abs(targetPitch - startPitch);
		this.durationMs = durationForDistance(distance);
		this.startedAtMs = System.currentTimeMillis();
		this.completionHandler = completionHandler;
		this.settling = false;
		this.active = true;
	}

	public void tick(Minecraft client) {
		if ((!active && !settling) || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (settling) {
			applyRotation(client, targetYaw, targetPitch);
			if (now >= settleUntilMs) {
				Runnable handler = completionHandler;
				stop();
				if (handler != null) {
					handler.run();
				}
			}
			return;
		}
		long elapsed = now - startedAtMs;
		float progress = durationMs <= 0 ? 1.0f : Math.min(1.0f, elapsed / (float) durationMs);
		SmoothLookMath.Angles next = interpolate(progress);
		applyRotation(client, next.yaw(), next.pitch());
		if (progress >= 1.0f) {
			applyRotation(client, targetYaw, targetPitch);
			active = false;
			settling = true;
			settleUntilMs = now + SETTLE_DURATION_MS;
		}
	}

	public void stop() {
		active = false;
		settling = false;
		completionHandler = null;
	}

	public boolean active() {
		return active || settling;
	}

	private SmoothLookMath.Angles interpolate(float progress) {
		float eased = SmoothLookMath.smoothstep(progress);
		float yaw = startYaw + SmoothLookMath.wrapDegrees(targetYaw - startYaw) * eased;
		float pitch = SmoothLookMath.clampPitch(startPitch + (targetPitch - startPitch) * eased);
		return new SmoothLookMath.Angles(yaw, pitch);
	}

	private void applyRotation(Minecraft client, float yaw, float pitch) {
		client.player.setYRot(yaw);
		client.player.setXRot(pitch);
		client.player.yRotO = yaw;
		client.player.xRotO = pitch;
	}

	private long durationForDistance(float degrees) {
		if (degrees <= 2.0f) {
			return 120L;
		}
		return Math.max(180L, Math.min(850L, (long) (degrees * 7.0f)));
	}
}
