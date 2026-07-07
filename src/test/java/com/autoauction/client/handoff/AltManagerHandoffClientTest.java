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
		FakeScheduleBridge.handoffMarked = false;
		FakeScheduleBridge.listingMarked = false;
		FakeScheduleBridge.craftReforgeMarked = false;
		FakeScheduleBridge.waitUntil = 0L;
		FakeScheduleBridge.nextScheduledAccount = "";
		FakeScheduleBridge.scheduleEnabled = false;
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
	void switchesToSchedulerTargetWhenProvided() {
		AltManagerHandoffClient client = new AltManagerHandoffClient(
			FakeAccountSwitcher.class.getName(),
			FakeScheduleBridge.class.getName()
		);

		AltManagerHandoffClient.HandoffResult result = client.switchToNextAccount("scheduled-uuid");

		assertTrue(result.switched());
		assertEquals("ScheduledPlayer", result.targetUsername());
		assertEquals("scheduled-uuid", result.targetUuid());
		assertEquals("scheduled-uuid", FakeAccountSwitcher.switchTarget);
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
			true,
			"schedule-key-1"
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
		assertEquals("schedule-key-1", policy.get().triggerKey());
	}

	@Test
	void readsSchedulerCraftReforgePolicyFieldsByReflection() {
		FakeScheduleBridge.policy = Optional.of(new FakeSchedulePolicy(
			"Scheduled",
			"scheduler-uuid",
			1,
			"CRAFT_REFORGE_ARMOR",
			0,
			"SHORT_ROTATION",
			false,
			"",
			false,
			"schedule-key-2",
			"Wise",
			"LIST_ARMOR",
			"Fierce"
		));
		AltManagerHandoffClient client = new AltManagerHandoffClient(
			FakeAccountSwitcher.class.getName(),
			FakeScheduleBridge.class.getName()
		);

		Optional<HandoffPolicySnapshot> policy = client.currentHandoffPolicy();

		assertTrue(policy.isPresent());
		assertEquals(true, policy.get().craftReforgeArmor());
		assertEquals("Wise", policy.get().craftReforge());
		assertEquals("LIST_ARMOR", policy.get().followUpAction());
		assertEquals("Fierce", policy.get().followUpReforge());
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

	@Test
	void notifiesSchedulerProgressAndReadsWaitUntil() {
		FakeScheduleBridge.waitUntil = 123_456L;
		FakeScheduleBridge.nextScheduledAccount = "scheduled-uuid";
		FakeScheduleBridge.scheduleEnabled = true;
		AltManagerHandoffClient client = new AltManagerHandoffClient(
			FakeAccountSwitcher.class.getName(),
			FakeScheduleBridge.class.getName()
		);

		assertTrue(client.markScheduleHandoffComplete("Old", "ScheduledPlayer"));
		assertTrue(client.markScheduleListingComplete("ScheduledPlayer"));
		assertTrue(client.markScheduleCraftReforgeComplete("ScheduledPlayer"));
		assertEquals(123_456L, client.currentScheduleWaitUntilEpochMs());
		assertEquals("scheduled-uuid", client.nextScheduledAccount("Old"));
		assertEquals(true, client.currentScheduleEnabled());
		assertTrue(FakeScheduleBridge.handoffMarked);
		assertTrue(FakeScheduleBridge.listingMarked);
		assertTrue(FakeScheduleBridge.craftReforgeMarked);
	}

	public static final class FakeAccountSwitcher {
		private static String switchTarget = "";
		private static int switchCalls;
		private static boolean proxyReady;
		private static Optional<FakePolicy> policy = Optional.empty();

		public static Optional<AccountSummary> nextAccount() {
			return Optional.of(new AccountSummary("NextPlayer", "next-uuid", "MICROSOFT"));
		}

		public static java.util.List<AccountSummary> listAccounts() {
			return java.util.List.of(
				new AccountSummary("NextPlayer", "next-uuid", "MICROSOFT"),
				new AccountSummary("ScheduledPlayer", "scheduled-uuid", "MICROSOFT")
			);
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
		private static boolean handoffMarked;
		private static boolean listingMarked;
		private static boolean craftReforgeMarked;
		private static long waitUntil;
		private static String nextScheduledAccount = "";
		private static boolean scheduleEnabled;

		public static Optional<FakeSchedulePolicy> currentSchedulePolicy() {
			return policy;
		}

		public static boolean markScheduleHandoffComplete(String fromUuidOrName, String toUuidOrName) {
			handoffMarked = "Old".equals(fromUuidOrName) && "ScheduledPlayer".equals(toUuidOrName);
			return handoffMarked;
		}

		public static boolean markScheduleListingComplete(String uuidOrName) {
			listingMarked = "ScheduledPlayer".equals(uuidOrName);
			return listingMarked;
		}

		public static boolean markScheduleCraftReforgeComplete(String uuidOrName) {
			craftReforgeMarked = "ScheduledPlayer".equals(uuidOrName);
			return craftReforgeMarked;
		}

		public static long currentScheduleWaitUntilEpochMs() {
			return waitUntil;
		}

		public static String nextScheduledAccount(String currentUuidOrName) {
			return nextScheduledAccount;
		}

		public static boolean isScheduleEnabled() {
			return scheduleEnabled;
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
		boolean waitAfterHandoff,
		String triggerKey,
		String reforge,
		String followUpAction,
		String followUpReforge
	) {
		public FakeSchedulePolicy(
			String username,
			String uuid,
			int killLimit,
			String action,
			int stopHours,
			String phase,
			boolean finalListing,
			String nextAccount,
			boolean waitAfterHandoff,
			String triggerKey
		) {
			this(username, uuid, killLimit, action, stopHours, phase, finalListing, nextAccount, waitAfterHandoff,
				triggerKey, "", "", "");
		}
	}
}
