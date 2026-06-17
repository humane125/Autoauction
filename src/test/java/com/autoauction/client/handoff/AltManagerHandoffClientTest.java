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

	public static final class FakeAccountSwitcher {
		private static String switchTarget = "";
		private static int switchCalls;

		public static Optional<AccountSummary> nextAccount() {
			return Optional.of(new AccountSummary("NextPlayer", "next-uuid", "MICROSOFT"));
		}

		public static boolean switchTo(String uuidOrName) {
			switchTarget = uuidOrName;
			switchCalls++;
			return true;
		}
	}

	public record AccountSummary(String name, String uuid, String type) {
	}
}
