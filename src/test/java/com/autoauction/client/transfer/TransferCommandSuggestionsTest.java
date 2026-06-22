package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCommandSuggestionsTest {
	@Test
	void suggestsConnectedReceiverUsernamesWithoutReservedWords() {
		assertEquals(List.of("ReceiverOne", "ReceiverTwo"), TransferCommandSuggestions.receiverUsernames(List.of(
			"ReceiverOne",
			"list",
			"ReceiverTwo",
			"receiverone",
			"run",
			" "
		)));
	}

	@Test
	void exposesDefaultDistillateItemNamesWhileAllowingCustomInputElsewhere() {
		List<String> items = TransferCommandSuggestions.itemNames();

		assertTrue(items.contains("Magma Cream Distillate"));
		assertTrue(items.contains("Blaze Rod Distillate"));
		assertTrue(items.contains("Glowstone Distillate"));
		assertTrue(items.contains("Nether Wart Distillate"));
		assertFalse(TransferCommandSuggestions.isReservedTransferWord("Magma Cream Distillate"));
	}
}
