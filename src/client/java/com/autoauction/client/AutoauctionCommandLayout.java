package com.autoauction.client;

import java.util.List;

final class AutoauctionCommandLayout {
	static final String MAIN_ROOT = "autoauction";
	static final String TEST_ROOT = "autoauctiontest";
	static final String TRANSFER_ROOT = "mf";

	static final List<String> MAIN_SUBCOMMANDS = List.of("start", "stop", "status", "island", "armor");
	static final List<String> TEST_SUBCOMMANDS = List.of("testtrigger", "testlisting", "dumpslots", "dumpstatus");
	static final List<String> TRANSFER_SUBCOMMANDS = List.of("list", "pair", "accept", "decline", "run", "switch", "cancel");

	private AutoauctionCommandLayout() {
	}
}
