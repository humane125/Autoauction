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
		return switchToNextAccount("");
	}

	public HandoffResult switchToNextAccount(String preferredTarget) {
		try {
			Class<?> switcherClass = Class.forName(accountSwitcherClassName);
			Method switchToMethod = switcherClass.getMethod("switchTo", String.class);

			Object account = preferredTarget == null || preferredTarget.isBlank()
				? nextAccount(switcherClass)
				: accountByTarget(switcherClass, preferredTarget);
			if (account == null) {
				return HandoffResult.notSwitched(preferredTarget == null || preferredTarget.isBlank()
					? "Alt Manager has no next account."
					: "Alt Manager scheduler target was not found: " + preferredTarget + ".");
			}

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

	private Object nextAccount(Class<?> switcherClass) throws ReflectiveOperationException {
		Method nextAccountMethod = switcherClass.getMethod("nextAccount");
		Object nextValue = nextAccountMethod.invoke(null);
		if (!(nextValue instanceof Optional<?> nextAccount) || nextAccount.isEmpty()) {
			return null;
		}
		return nextAccount.get();
	}

	private Object accountByTarget(Class<?> switcherClass, String preferredTarget) throws ReflectiveOperationException {
		Method listAccountsMethod = switcherClass.getMethod("listAccounts");
		Object value = listAccountsMethod.invoke(null);
		if (!(value instanceof Iterable<?> accounts)) {
			return null;
		}
		for (Object account : accounts) {
			String username = stringRecordValue(account, "name");
			String uuid = stringRecordValue(account, "uuid");
			if (preferredTarget.equalsIgnoreCase(username) || preferredTarget.equalsIgnoreCase(uuid)) {
				return account;
			}
		}
		return null;
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

	public boolean markScheduleHandoffComplete(String fromUuidOrName, String toUuidOrName) {
		return booleanScheduleCall("markScheduleHandoffComplete", fromUuidOrName, toUuidOrName);
	}

	public boolean markScheduleListingComplete(String uuidOrName) {
		return booleanScheduleCall("markScheduleListingComplete", uuidOrName);
	}

	public boolean markScheduleCraftReforgeComplete(String uuidOrName) {
		return booleanScheduleCall("markScheduleCraftReforgeComplete", uuidOrName);
	}

	public boolean markScheduleStartClaimed(String uuidOrName) {
		return booleanScheduleCall("markScheduleStartClaimed", uuidOrName);
	}

	public boolean markScheduleTimeEntryClaimed(String id) {
		return booleanScheduleCall("markScheduleTimeEntryClaimed", id);
	}

	public Optional<ScheduleTimeEntrySnapshot> currentDueScheduleTimeEntry() {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("currentDueScheduleTimeEntry");
			Object value = method.invoke(null);
			if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(scheduleTimeEntrySnapshot(optional.get()));
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return Optional.empty();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Optional.empty();
		}
	}

	public long currentScheduleWaitUntilEpochMs() {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("currentScheduleWaitUntilEpochMs");
			Object value = method.invoke(null);
			return value instanceof Number number ? number.longValue() : 0L;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return 0L;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0L;
		}
	}

	public long currentScheduleStartAtEpochMs() {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("currentScheduleStartAtEpochMs");
			Object value = method.invoke(null);
			return value instanceof Number number ? number.longValue() : 0L;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return 0L;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0L;
		}
	}

	public String nextScheduledAccount(String currentUuidOrName) {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("nextScheduledAccount", String.class);
			Object value = method.invoke(null, currentUuidOrName);
			return String.valueOf(value == null ? "" : value);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return "";
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "";
		}
	}

	public boolean currentScheduleEnabled() {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method = bridgeClass.getMethod("isScheduleEnabled");
			Object value = method.invoke(null);
			return value instanceof Boolean enabled && enabled;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
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

	private boolean booleanScheduleCall(String methodName, String... args) {
		try {
			Class<?> bridgeClass = Class.forName(scheduleBridgeClassName);
			Method method;
			Object value;
			if (args.length == 1) {
				method = bridgeClass.getMethod(methodName, String.class);
				value = method.invoke(null, args[0]);
			} else if (args.length == 2) {
				method = bridgeClass.getMethod(methodName, String.class, String.class);
				value = method.invoke(null, args[0], args[1]);
			} else {
				return false;
			}
			return value instanceof Boolean bool && bool;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
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
			optionalBooleanRecordValue(policy, "waitAfterHandoff"),
			optionalStringRecordValue(policy, "triggerKey"),
			optionalStringRecordValue(policy, "reforge"),
			optionalStringRecordValue(policy, "followUpAction"),
			optionalStringRecordValue(policy, "followUpReforge")
		);
	}

	private static ScheduleTimeEntrySnapshot scheduleTimeEntrySnapshot(Object entry)
		throws ReflectiveOperationException {
		return new ScheduleTimeEntrySnapshot(
			stringRecordValue(entry, "id"),
			stringRecordValue(entry, "type"),
			stringRecordValue(entry, "rawInput"),
			longRecordValue(entry, "scheduledEpochMs"),
			booleanRecordValue(entry, "daily"),
			booleanRecordValue(entry, "enabled")
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

	private static long longRecordValue(Object value, String methodName) throws ReflectiveOperationException {
		Object result = value.getClass().getMethod(methodName).invoke(value);
		return result instanceof Number number ? number.longValue() : 0L;
	}

	private static boolean booleanRecordValue(Object value, String methodName) throws ReflectiveOperationException {
		Object result = value.getClass().getMethod(methodName).invoke(value);
		return result instanceof Boolean bool && bool;
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

	public record ScheduleTimeEntrySnapshot(
		String id,
		String type,
		String rawInput,
		long scheduledEpochMs,
		boolean daily,
		boolean enabled
	) {
		public boolean start() {
			return "START".equalsIgnoreCase(type);
		}

		public boolean stop() {
			return "STOP".equalsIgnoreCase(type);
		}
	}
}
