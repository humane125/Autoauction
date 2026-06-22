package com.autoauction.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AutoauctionCommandLayoutTest {
	@Test
	void commandRootsAreSplitByPurpose() {
		assertEquals("autoauction", AutoauctionCommandLayout.MAIN_ROOT);
		assertEquals("autoauctiontest", AutoauctionCommandLayout.TEST_ROOT);
		assertEquals("mf", AutoauctionCommandLayout.TRANSFER_ROOT);
	}

	@Test
	void mainCommandDoesNotExposeTestOrTransferSubcommands() {
		assertEquals(List.of("start", "stop", "status", "island", "armor"), AutoauctionCommandLayout.MAIN_SUBCOMMANDS);
		assertFalse(AutoauctionCommandLayout.MAIN_SUBCOMMANDS.contains("testtrigger"));
		assertFalse(AutoauctionCommandLayout.MAIN_SUBCOMMANDS.contains("testlisting"));
		assertFalse(AutoauctionCommandLayout.MAIN_SUBCOMMANDS.contains("transfer"));
	}

	@Test
	void testAndTransferCommandsHaveTheirOwnRoots() {
		assertEquals(List.of("testtrigger", "testlisting", "dumpslots", "dumpstatus"), AutoauctionCommandLayout.TEST_SUBCOMMANDS);
		assertEquals(List.of("list", "accept", "decline", "run", "cancel", "<receiverUsername> <itemName>"), AutoauctionCommandLayout.TRANSFER_SUBCOMMANDS);
	}
}
