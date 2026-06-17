package com.autoauction.client.handoff;

import java.lang.reflect.Method;
import java.util.Optional;

public final class AltManagerHandoffClient {
	private static final String DEFAULT_ACCOUNT_SWITCHER = "com.altmanager.client.account.AccountSwitcher";

	private final String accountSwitcherClassName;

	public AltManagerHandoffClient() {
		this(DEFAULT_ACCOUNT_SWITCHER);
	}

	AltManagerHandoffClient(String accountSwitcherClassName) {
		this.accountSwitcherClassName = accountSwitcherClassName;
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

	private static String stringRecordValue(Object value, String methodName) throws ReflectiveOperationException {
		Object result = value.getClass().getMethod(methodName).invoke(value);
		return String.valueOf(result == null ? "" : result);
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
