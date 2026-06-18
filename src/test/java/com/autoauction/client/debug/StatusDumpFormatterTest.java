package com.autoauction.client.debug;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusDumpFormatterTest {
	@Test
	void formatsScoreboardAndTabListSectionsForLogs() {
		List<String> lines = StatusDumpFormatter.format(
			List.of("title=SKYBLOCK", "line=Your Island", "line=Purse: 1,875,543"),
			List.of("Rivoh89", "Area: Hub", "Cookie Buff 3 days")
		);

		assertEquals(List.of(
			"AutoAuction status dump begin",
			"scoreboard[0] title=SKYBLOCK",
			"scoreboard[1] line=Your Island",
			"scoreboard[2] line=Purse: 1,875,543",
			"tablist[0] Rivoh89",
			"tablist[1] Area: Hub",
			"tablist[2] Cookie Buff 3 days",
			"AutoAuction status dump end"
		), lines);
	}
}
