package com.autoauction.client.handoff;

import java.lang.reflect.Method;
import java.util.Optional;

public final class AltManagerHandoffClient {
	private static final String DEFAULT_ACCOUNT_SWITCHER = "com.altmanager.client.account.AccountSwitcher";
	private static final String DEFAULT_SCHEDULE_BRIDGE = "com.altmanager.client.scheduler.TaskScheduleBridge";

	private final String accountSwitcherClassName;
	private final String scheduleBridgeClassName;

	public AltManagerHandoffClient() {
		this(DEFAULT_ACCOUNT_SWITCHER, DEFAULT_SCHEDULE_BRIDGE);
	}

	AltManagerHandoffClient(String accountSwitcherClassName) {
		this(accountSwitcherClassName, DEFAULT_SCHEDULE_BRIDGE);
	}

	AltManagerHandoffClient(String accountSwitcherClassName, String scheduleBridgeClassName) {
		this.accountSwitcherClassName = accountSwitcherClassName;
		this.scheduleBridgeClassName = scheduleBridgeClassName;
	}

	public HandoffResult switchToNextAccount() {
		try {
			Class<?> switcherClass = Class.forName(accountSwitcherClassName);
			Method nextAccountMethod = switcherClass.getMethod("nextAccount");
			Method switchToMethod = switcherClass.getMethod("switchTo", String.class);

			Object nextValue = nextAccountMethod.invoke(null);
			if (!(nextValue instanceof Optional<?> nextAccount) || nextAccount.isEmpty()) {
				return HandoffResult.notSwitched("Alt Manager has no next account.");
			}

			Object account = nextAccount.get();
			String username = stringRecordValue(account, "name");
			String uuid = stringRecordValue(account, "uuid");
			String switchTarget = uuid.isBlank() ? username : uuid;
			if (switchTarget.isBlank()) {
				return HandoffResult.notSwitched("Alt Manager next account has no name or UUID.");
			}

			Object switchedValue = switchToMethod.invoke(null, switchTarget);
			boolean switched = switchedValue instanceof Boolean value && value;
			return switched
				? HandoffResult.switched(username, uuid)
				: HandoffResult.notSwitched("Alt Manager rejected account switch to " + switchTarget + ".");
		} catch (ClassNotFoundException e) {
			return HandoffResult.notSwitched("Alt Manager is not loaded.");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return HandoffResult.notSwitched("Alt Manager handoff failed: " + e.getMessage());
		}
	}

	public ProxyReadiness proxyReadiness(String username, String uuid) {
		try {
			Class<?> switcherClass = Class.forName(accountSwitcherClassName);
			Method isProxyReadyMethod = switcherClass.getMethod("isProxyReady", String.class, String.class);
			Object readyValue = isProxyReadyMethod.invoke(null, username, uuid);
			return readyValue instanceof Boolean ready && ready ? ProxyReadiness.READY : ProxyReadiness.WAITING;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return ProxyReadiness.WAITING;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return ProxyReadiness.WAITING;
		}
	}

	public Optional<HandoffPolicySnapshot> currentHandoffPolicy() {
		Optional<HandoffPolicySnapshot> schedulerPolicy = currentSchedulePolicy();
		if (schedulerPolicy.isPresent()) {
			return schedulerPolicy;
		}
		try {
			Class<?> switcherClass = Class.forName(accountSwitcherClassName);
			Method method = switcherClass.getMethod("currentHandoffPolicy");
			Object value = method.invoke(null);
			if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
				return Optional.empty();
			}
			Object policy = optional.get();
			return Optional.of(policySnapshot(policy));
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return Optional.empty();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Optional.empty();
		}
	}

	private Optional<HandoffPolicySnapshot> currentSchedulePolicy() {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("currentSchedulePolicy");
			Object value = method.invoke(null);
			if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(policySnapshot(optional.get()));
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return Optional.empty();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Optional.empty();
		}
	}

	private static HandoffPolicySnapshot policySnapshot(Object policy) throws ReflectiveOperationException {
		return new HandoffPolicySnapshot(
			stringRecordValue(policy, "username"),
			stringRecordValue(policy, "uuid"),
			intRecordValue(policy, "killLimit"),
			stringRecordValue(policy, "action"),
			intRecordValue(policy, "stopHours"),
			optionalStringRecordValue(policy, "phase"),
			optionalBooleanRecordValue(policy, "finalListing"),
			optionalStringRecordValue(policy, "nextAccount"),
			optionalBooleanRecordValue(policy, "waitAfterHandoff")
		);
	}

	private static String stringRecordValue(Object value, String methodName) throws ReflectiveOperationException {
		Object result = value.getClass().getMethod(methodName).invoke(value);
		return String.valueOf(result == null ? "" : result);
	}

	private static int intRecordValue(Object value, String methodName) throws ReflectiveOperationException {
		Object result = value.getClass().getMethod(methodName).invoke(value);
		return result instanceof Number number ? number.intValue() : 0;
	}

	private static String optionalStringRecordValue(Object value, String methodName) {
		try {
			return stringRecordValue(value, methodName);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return "";
		}
	}

	private static boolean optionalBooleanRecordValue(Object value, String methodName) {
		try {
			Object result = value.getClass().getMethod(methodName).invoke(value);
			return result instanceof Boolean bool && bool;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return false;
		}
	}

	public record HandoffResult(boolean switched, String targetUsername, String targetUuid, String message) {
		public static HandoffResult switched(String targetUsername, String targetUuid) {
			return new HandoffResult(true, targetUsername, targetUuid, "");
		}

		public static HandoffResult notSwitched(String message) {
			return new HandoffResult(false, "", "", message);
		}
	}

	public enum ProxyReadiness {
		READY,
		WAITING
	}
}
