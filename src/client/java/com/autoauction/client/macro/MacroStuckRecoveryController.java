package com.autoauction.client.macro;

import java.util.function.Consumer;

public final class MacroStuckRecoveryController {
	private static final long STATIONARY_THRESHOLD_MS = 20_000L;
	private static final long HUB_WAIT_MS = 5_000L;
	private static final long RECOVERY_COOLDOWN_MS = 90_000L;
	private static final double MOVEMENT_EPSILON_SQR = 0.0004;
	private static final String HUB_COMMAND = "/hub";

	private State state = State.IDLE;
	private long stationarySince = -1L;
	private long stateStartedAt = -1L;
	private long nextRecoveryAllowedAt = 0L;
	private Position lastPosition;

	public void tick(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		Consumer<String> logSink
	) {
		if (snapshot == null || macroController == null || commandSink == null || logSink == null) {
			return;
		}
		if (!snapshot.playerAvailable()) {
			resetMovement();
			state = State.IDLE;
			return;
		}
		if (state != State.IDLE && !macroController.desiredOn()) {
			resetRecovery();
			resetMovement();
			logSink.accept("Macro stuck recovery cancelled because macro desired state is OFF.");
			return;
		}
		if (state == State.IDLE && !macroEligibleForRecovery(macroController)) {
			resetMovement();
			return;
		}
		updateMovement(snapshot);
		switch (state) {
			case IDLE -> maybeStartRecovery(snapshot, macroController, commandSink, logSink);
			case HUB_WAIT -> tickHubWait(snapshot, macroController, commandSink, logSink);
			case RESTARTING -> tickRestart(snapshot, macroController, commandSink, logSink);
		}
	}

	private boolean macroEligibleForRecovery(NebulaMacroController macroController) {
		return macroController.desiredOn()
			&& macroController.observedState() == NebulaMacroController.ObservedState.ON;
	}

	private void maybeStartRecovery(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		Consumer<String> logSink
	) {
		if (!macroController.desiredOn()
			|| macroController.observedState() != NebulaMacroController.ObservedState.ON
			|| stationarySince < 0L
			|| snapshot.nowMs() - stationarySince < STATIONARY_THRESHOLD_MS
			|| snapshot.nowMs() < nextRecoveryAllowedAt) {
			return;
		}
		if (snapshot.inSkyBlock()) {
			commandSink.accept(HUB_COMMAND);
			state = State.HUB_WAIT;
			stateStartedAt = snapshot.nowMs();
			logSink.accept("Macro appears stuck for 20s; sent /hub before Nebula restart.");
			return;
		}
		state = State.RESTARTING;
		stateStartedAt = snapshot.nowMs();
		logSink.accept("Macro appears stuck for 20s outside SkyBlock; restarting Nebula macro.");
		tickRestart(snapshot, macroController, commandSink, logSink);
	}

	private void tickHubWait(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		Consumer<String> logSink
	) {
		if (snapshot.nowMs() - stateStartedAt < HUB_WAIT_MS) {
			return;
		}
		state = State.RESTARTING;
		stateStartedAt = snapshot.nowMs();
		tickRestart(snapshot, macroController, commandSink, logSink);
	}

	private void tickRestart(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		Consumer<String> logSink
	) {
		NebulaMacroController.EnsureResult result = macroController.restartOn(commandSink, snapshot.nowMs());
		if (result == NebulaMacroController.EnsureResult.PENDING) {
			return;
		}
		if (result == NebulaMacroController.EnsureResult.FAILED) {
			logSink.accept("Macro stuck recovery timed out while restarting Nebula macro.");
		} else {
			logSink.accept("Macro stuck recovery completed; Nebula macro rearmed.");
		}
		nextRecoveryAllowedAt = snapshot.nowMs() + RECOVERY_COOLDOWN_MS;
		state = State.IDLE;
		stationarySince = snapshot.nowMs();
	}

	private void updateMovement(Snapshot snapshot) {
		Position position = snapshot.position();
		if (lastPosition == null) {
			lastPosition = position;
			stationarySince = snapshot.nowMs();
			return;
		}
		if (position.distanceToSqr(lastPosition) > MOVEMENT_EPSILON_SQR) {
			lastPosition = position;
			stationarySince = snapshot.nowMs();
		}
	}

	private void resetMovement() {
		lastPosition = null;
		stationarySince = -1L;
	}

	private void resetRecovery() {
		state = State.IDLE;
		stateStartedAt = -1L;
		nextRecoveryAllowedAt = 0L;
	}

	public record Snapshot(Position position, boolean playerAvailable, boolean inSkyBlock, long nowMs) {
	}

	public record Position(double x, double y, double z) {
		double distanceToSqr(Position other) {
			double dx = x - other.x;
			double dy = y - other.y;
			double dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	private enum State {
		IDLE,
		HUB_WAIT,
		RESTARTING
	}
}
