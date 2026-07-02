package com.autoauction.client.handoff;

public final class HandoffPolicySwitchPreparation {
	private final long beforeIslandMs;
	private final long afterIslandMs;
	private State state = State.WAIT_BEFORE_ISLAND;
	private long stateStartedAt = -1L;

	public HandoffPolicySwitchPreparation(long beforeIslandMs, long afterIslandMs) {
		this.beforeIslandMs = Math.max(0L, beforeIslandMs);
		this.afterIslandMs = Math.max(0L, afterIslandMs);
	}

	public Step tick(long now) {
		if (state == State.READY) {
			return Step.ready();
		}
		if (stateStartedAt < 0L) {
			stateStartedAt = now;
			return Step.waiting();
		}
		if (state == State.WAIT_BEFORE_ISLAND) {
			if (now - stateStartedAt < beforeIslandMs) {
				return Step.waiting();
			}
			state = State.WAIT_AFTER_ISLAND;
			stateStartedAt = now;
			return Step.command("/is");
		}
		if (now - stateStartedAt < afterIslandMs) {
			return Step.waiting();
		}
		state = State.READY;
		return Step.ready();
	}

	private enum State {
		WAIT_BEFORE_ISLAND,
		WAIT_AFTER_ISLAND,
		READY
	}

	public record Step(Type type, String command) {
		public static Step waiting() {
			return new Step(Type.WAITING, "");
		}

		public static Step command(String command) {
			return new Step(Type.COMMAND, command);
		}

		public static Step ready() {
			return new Step(Type.READY, "");
		}
	}

	public enum Type {
		WAITING,
		COMMAND,
		READY
	}
}
