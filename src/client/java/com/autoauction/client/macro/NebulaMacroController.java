package com.autoauction.client.macro;

import java.util.Locale;
import java.util.function.Consumer;

public final class NebulaMacroController {
	public static final String TOGGLE_COMMAND = "/n toggleMacro combat macro";
	private static final long ENSURE_TIMEOUT_MS = 8_000L;

	private ObservedState observedState = ObservedState.UNKNOWN;
	private Operation operation = Operation.NONE;
	private long operationStartedAt;
	private boolean toggleSent;
	private boolean desiredOn;

	public ObservedState observedState() {
		return observedState;
	}

	public boolean desiredOn() {
		return desiredOn;
	}

	public void onChatMessage(String message) {
		String normalized = normalize(message);
		String compact = normalized.replace(" ", "");
		if (!normalized.contains("nebulaclient") || !compact.contains("combatmacro:")) {
			return;
		}
		if (compact.contains("combatmacro:enabled")) {
			observedState = ObservedState.ON;
		} else if (compact.contains("combatmacro:disabled")) {
			observedState = ObservedState.OFF;
		}
	}

	public EnsureResult ensureOn(Consumer<String> commandSink, long nowMs) {
		desiredOn = true;
		if (observedState == ObservedState.ON) {
			clearOperation();
			return EnsureResult.COMPLETE;
		}
		if (operation != Operation.ENSURE_ON) {
			startOperation(Operation.ENSURE_ON, nowMs);
		}
		return waitForState(commandSink, nowMs, ObservedState.ON);
	}

	public EnsureResult ensureOff(Consumer<String> commandSink, long nowMs) {
		desiredOn = false;
		if (observedState == ObservedState.OFF) {
			clearOperation();
			return EnsureResult.COMPLETE;
		}
		if (operation != Operation.ENSURE_OFF && operation != Operation.ENSURE_OFF_AFTER_ENABLED) {
			startOperation(Operation.ENSURE_OFF, nowMs);
		}
		if (operation == Operation.ENSURE_OFF_AFTER_ENABLED) {
			return waitForState(commandSink, nowMs, ObservedState.OFF);
		}
		if (!toggleSent) {
			sendToggle(commandSink);
			return EnsureResult.PENDING;
		}
		if (timedOut(nowMs)) {
			clearOperation();
			return EnsureResult.FAILED;
		}
		if (observedState == ObservedState.ON) {
			startOperation(Operation.ENSURE_OFF_AFTER_ENABLED, nowMs);
			sendToggle(commandSink);
		}
		return EnsureResult.PENDING;
	}

	public AutoRestoreResult autoRestoreIfDisabled(Consumer<String> commandSink, long nowMs) {
		if (!desiredOn || observedState != ObservedState.OFF) {
			return AutoRestoreResult.IDLE;
		}
		boolean sendingFirstToggle = operation != Operation.ENSURE_ON || !toggleSent;
		EnsureResult result = ensureOn(commandSink, nowMs);
		if (result == EnsureResult.COMPLETE) {
			return AutoRestoreResult.COMPLETE;
		}
		if (result == EnsureResult.FAILED) {
			return AutoRestoreResult.FAILED;
		}
		return sendingFirstToggle ? AutoRestoreResult.STARTED : AutoRestoreResult.PENDING;
	}

	public void recordManualToggleIntent() {
		desiredOn = false;
		clearOperation();
	}

	public void resetOperation() {
		clearOperation();
	}

	public static boolean isToggleCommand(String command) {
		String normalized = String.valueOf(command == null ? "" : command)
			.trim()
			.replaceFirst("^[/.]+", "")
			.replaceAll("\\s+", " ")
			.toLowerCase(Locale.ROOT);
		return normalized.equals("n togglemacro combat macro");
	}

	private EnsureResult waitForState(Consumer<String> commandSink, long nowMs, ObservedState targetState) {
		if (!toggleSent) {
			sendToggle(commandSink);
			return EnsureResult.PENDING;
		}
		if (observedState == targetState) {
			clearOperation();
			return EnsureResult.COMPLETE;
		}
		if (timedOut(nowMs)) {
			clearOperation();
			return EnsureResult.FAILED;
		}
		return EnsureResult.PENDING;
	}

	private void startOperation(Operation nextOperation, long nowMs) {
		operation = nextOperation;
		operationStartedAt = nowMs;
		toggleSent = false;
	}

	private void sendToggle(Consumer<String> commandSink) {
		commandSink.accept(TOGGLE_COMMAND);
		toggleSent = true;
	}

	private boolean timedOut(long nowMs) {
		return nowMs - operationStartedAt >= ENSURE_TIMEOUT_MS;
	}

	private void clearOperation() {
		operation = Operation.NONE;
		operationStartedAt = 0L;
		toggleSent = false;
	}

	private static String normalize(String message) {
		return String.valueOf(message == null ? "" : message)
			.replaceAll("(?i)(?:\u00C2?\u00A7|&)[0-9A-FK-OR]", "")
			.toLowerCase(Locale.ROOT)
			.trim();
	}

	public enum ObservedState {
		UNKNOWN,
		ON,
		OFF
	}

	public enum EnsureResult {
		COMPLETE,
		PENDING,
		FAILED
	}

	public enum AutoRestoreResult {
		IDLE,
		STARTED,
		PENDING,
		COMPLETE,
		FAILED
	}

	private enum Operation {
		NONE,
		ENSURE_ON,
		ENSURE_OFF,
		ENSURE_OFF_AFTER_ENABLED
	}
}
