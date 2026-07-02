package com.autoauction.client.handoff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltManagerHandoffClientTest {
	@BeforeEach
	void resetFakeSwitcher() {
		FakeAccountSwitcher.switchTarget = "";
		FakeAccountSwitcher.switchCalls = 0;
		FakeAccountSwitcher.proxyReady = false;
		FakeAccountSwitcher.policy = Optional.empty();
		FakeScheduleBridge.policy = Optional.empty();
	}

	@Test
	void switchesToNextAltManagerAccountByUuid() {
		AltManagerHandoffClient client = new AltManagerHandoffClient(FakeAccountSwitcher.class.getName());

		AltManagerHandoffClient.HandoffResult result = client.switchToNextAccount();

		assertTrue(result.switched());
		assertEquals("NextPlayer", result.targetUsername());
		assertEquals("next-uuid", result.targetUuid());
		assertEquals("next-uuid", FakeAccountSwitcher.switchTarget);
		assertEquals(1, FakeAccountSwitcher.switchCalls);
	}

	@Test
	void checksWhetherAltManagerProxyIsReadyForTargetAccount() {
		AltManagerHandoffClient client = new AltManagerHandoffClient(FakeAccountSwitcher.class.getName());

		assertEquals(AltManagerHandoffClient.ProxyReadiness.WAITING,
			client.proxyReadiness("NextPlayer", "next-uuid"));

		FakeAccountSwitcher.proxyReady = true;

		assertEquals(AltManagerHandoffClient.ProxyReadiness.READY,
			client.proxyReadiness("NextPlayer", "next-uuid"));
	}

	@Test
	void readsCurrentHandoffPolicyByReflection() {
		FakeAccountSwitcher.policy = Optional.of(new FakePolicy("MacroOne", "uuid-one", 12_500, "NEXT_ACCOUNT", 0));
		AltManagerHandoffClient client = new AltManagerHandoffClient(FakeAccountSwitcher.class.getName());

		Optional<HandoffPolicySnapshot> policy = client.currentHandoffPolicy();

		assertTrue(policy.isPresent());
		assertEquals("MacroOne", policy.get().username());
		assertEquals("uuid-one", policy.get().uuid());
		assertEquals(12_500, policy.get().killLimit());
		assertEquals("NEXT_ACCOUNT", policy.get().action());
	}

	@Test
	void schedulerPolicyOverridesManualPolicy() {
		FakeAccountSwitcher.policy = Optional.of(new FakePolicy("Manual", "manual-uuid", 20_000, "NEXT_ACCOUNT", 0));
		FakeScheduleBridge.policy = Optional.of(new FakeSchedulePolicy(
			"Scheduled",
			"scheduler-uuid",
			12_500,
			"NEXT_ACCOUNT",
			0,
			"SHORT_ROTATION",
			false,
			"next-uuid",
			true
		));
		AltManagerHandoffClient client = new AltManagerHandoffClient(
			FakeAccountSwitcher.class.getName(),
			FakeScheduleBridge.class.getName()
		);

		Optional<HandoffPolicySnapshot> policy = client.currentHandoffPolicy();

		assertTrue(policy.isPresent());
		assertEquals("Scheduled", policy.get().username());
		assertEquals("scheduler-uuid", policy.get().uuid());
		assertEquals(12_500, policy.get().killLimit());
		assertEquals("NEXT_ACCOUNT", policy.get().action());
		assertEquals("SHORT_ROTATION", policy.get().phase());
		assertEquals("next-uuid", policy.get().nextTarget());
		assertTrue(policy.get().waitAfterHandoff());
	}

	@Test
	void fallsBackToManualPolicyWhenSchedulerPolicyIsEmpty() {
		FakeAccountSwitcher.policy = Optional.of(new FakePolicy("Manual", "manual-uuid", 20_000, "NEXT_ACCOUNT", 0));
		AltManagerHandoffClient client = new AltManagerHandoffClient(
			FakeAccountSwitcher.class.getName(),
			FakeScheduleBridge.class.getName()
		);

		Optional<HandoffPolicySnapshot> policy = client.currentHandoffPolicy();

		assertTrue(policy.isPresent());
		assertEquals("Manual", policy.get().username());
		assertEquals("manual-uuid", policy.get().uuid());
		assertEquals(20_000, policy.get().killLimit());
	}

	public static final class FakeAccountSwitcher {
		private static String switchTarget = "";
		private static int switchCalls;
		private static boolean proxyReady;
		private static Optional<FakePolicy> policy = Optional.empty();

		public static Optional<AccountSummary> nextAccount() {
			return Optional.of(new AccountSummary("NextPlayer", "next-uuid", "MICROSOFT"));
		}

		public static boolean switchTo(String uuidOrName) {
			switchTarget = uuidOrName;
			switchCalls++;
			return true;
		}

		public static boolean isProxyReady(String username, String uuid) {
			return proxyReady
				&& "NextPlayer".equals(username)
				&& "next-uuid".equals(uuid);
		}

		public static Optional<FakePolicy> currentHandoffPolicy() {
			return policy;
		}
	}

	public static final class FakeScheduleBridge {
		private static Optional<FakeSchedulePolicy> policy = Optional.empty();

		public static Optional<FakeSchedulePolicy> currentSchedulePolicy() {
			return policy;
		}
	}

	public record AccountSummary(String name, String uuid, String type) {
	}

	public record FakePolicy(String username, String uuid, int killLimit, String action, int stopHours) {
	}

	public record FakeSchedulePolicy(
		String username,
		String uuid,
		int killLimit,
		String action,
		int stopHours,
		String phase,
		boolean finalListing,
		String nextAccount,
		boolean waitAfterHandoff
	) {
	}
}
