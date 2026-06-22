package com.autoauction.client.transfer;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferChatComponentsTest {
	@Test
	void addsPairSuggestionButtonToConnectedAccounts() {
		Component message = TransferChatComponents.forMessage("- ReceiverOne (hypixel)");

		assertEquals("- ReceiverOne (hypixel) [Pair]", message.getString());
		assertEquals("/mf pair ReceiverOne ", clickCommand(message, "Pair"));
		assertInstanceOf(ClickEvent.SuggestCommand.class, clickEvent(message, "Pair"));
	}

	@Test
	void addsAcceptAndDeclineRunButtonsToIncomingInvite() {
		Component message = TransferChatComponents.forMessage(
			"AutoAuction transfer invite from SenderPlayer for ENCHANTED DIAMOND. Run /mf accept SenderPlayer or /mf decline SenderPlayer."
		);

		assertTrue(message.getString().contains("[Accept]"));
		assertTrue(message.getString().contains("[Decline]"));
		assertEquals("/mf accept SenderPlayer", clickCommand(message, "Accept"));
		assertEquals("/mf decline SenderPlayer", clickCommand(message, "Decline"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Accept"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Decline"));
	}

	@Test
	void addsRunAndCancelButtonsForPairedSender() {
		Component message = TransferChatComponents.forMessage(
			"AutoAuction transfer paired as sender with ReceiverPlayer for ENCHANTED DIAMOND. Bazaar automation is waiting for menu dumps."
		);

		assertTrue(message.getString().contains("[Run]"));
		assertTrue(message.getString().contains("[Cancel]"));
		assertEquals("/mf run ", clickCommand(message, "Run"));
		assertEquals("/mf cancel", clickCommand(message, "Cancel"));
		assertInstanceOf(ClickEvent.SuggestCommand.class, clickEvent(message, "Run"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Cancel"));
	}

	private static ClickEvent clickEvent(Component component, String label) {
		for (Component sibling : component.getSiblings()) {
			if (sibling.getString().contains(label)) {
				return sibling.getStyle().getClickEvent();
			}
		}
		throw new AssertionError("No clickable sibling for " + label + " in " + component.getString());
	}

	private static String clickCommand(Component component, String label) {
		ClickEvent event = clickEvent(component, label);
		if (event instanceof ClickEvent.SuggestCommand suggest) {
			return suggest.command();
		}
		if (event instanceof ClickEvent.RunCommand run) {
			return run.command();
		}
		throw new AssertionError("Unexpected click event " + event);
	}
}
