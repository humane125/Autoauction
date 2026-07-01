package com.autoauction.client.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatsSendPolicyTest {
	@Test
	void rateLimitsChangedStatsUntilIntervalExpires() {
		AccountStatsSnapshot previous = new AccountStatsSnapshot(1_000_000L, 10, 10, 10, 10);
		AccountStatsSnapshot changed = new AccountStatsSnapshot(1_000_150L, 11, 10, 10, 10);

		assertTrue(AccountStatsSendPolicy.shouldSend(changed, null, 0L, 1_000L, 30_000L));
		assertFalse(AccountStatsSendPolicy.shouldSend(changed, previous, 1_000L, 20_000L, 30_000L));
		assertTrue(AccountStatsSendPolicy.shouldSend(changed, previous, 1_000L, 31_000L, 30_000L));
	}

	@Test
	void sendsImmediatelyWhenMacroingStateChanges() {
		AccountStatsSnapshot notMacroing = new AccountStatsSnapshot(1_000_000L, 10, 10, 10, 10, false);
		AccountStatsSnapshot macroing = new AccountStatsSnapshot(1_000_000L, 10, 10, 10, 10, true);

		assertTrue(AccountStatsSendPolicy.shouldSend(macroing, notMacroing, 1_000L, 2_000L, 30_000L));
		assertTrue(AccountStatsSendPolicy.shouldSend(notMacroing, macroing, 1_000L, 2_000L, 30_000L));
	}
}
