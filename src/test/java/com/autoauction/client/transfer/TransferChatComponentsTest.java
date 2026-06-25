package com.autoauction.client.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
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
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD),
			clickableSibling(message, "Pair").getStyle().getColor());
		assertTrue(clickableSibling(message, "Pair").getStyle().isBold());
		assertEquals(false, clickableSibling(message, "Pair").getStyle().isUnderlined());
		assertHoverText(message, "Pair", "Click to fill /mf pair ReceiverOne <item>", ChatFormatting.AQUA);
	}

	@Test
	void addsAcceptAndDeclineRunButtonsToIncomingInvite() {
		Component message = TransferChatComponents.forMessage(
			"AutoAuction transfer invite from SenderPlayer for ENCHANTED DIAMOND. Run /mf accept SenderPlayer or /mf decline SenderPlayer."
		);

		assertTrue(message.getString().contains(" [Accept]"));
		assertTrue(message.getString().contains(" [Decline]"));
		assertEquals("/mf accept SenderPlayer", clickCommand(message, "Accept"));
		assertEquals("/mf decline SenderPlayer", clickCommand(message, "Decline"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Accept"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Decline"));
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN),
			clickableSibling(message, "Accept").getStyle().getColor());
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED),
			clickableSibling(message, "Decline").getStyle().getColor());
		assertEquals(false, clickableSibling(message, "Accept").getStyle().isUnderlined());
		assertEquals(false, clickableSibling(message, "Decline").getStyle().isUnderlined());
		assertHoverText(message, "Accept", "Accept SenderPlayer's transfer invite", ChatFormatting.GREEN);
		assertHoverText(message, "Decline", "Decline SenderPlayer's transfer invite", ChatFormatting.RED);
	}

	@Test
	void addsRunAndCancelButtonsForPairedSender() {
		Component message = TransferChatComponents.forMessage(
			"AutoAuction transfer paired. You are sender for ENCHANTED DIAMOND with ReceiverPlayer. Keep the transfer items and coins on this account, then run /mf run <target> when ready."
		);

		assertTrue(message.getString().contains(" [Run]"));
		assertTrue(message.getString().contains(" [Switch]"));
		assertTrue(message.getString().contains(" [Cancel]"));
		assertEquals("/mf run ", clickCommand(message, "Run"));
		assertEquals("/mf switch", clickCommand(message, "Switch"));
		assertEquals("/mf cancel", clickCommand(message, "Cancel"));
		assertInstanceOf(ClickEvent.SuggestCommand.class, clickEvent(message, "Run"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Switch"));
		assertInstanceOf(ClickEvent.RunCommand.class, clickEvent(message, "Cancel"));
		assertColoredText(message, "sender", ChatFormatting.GOLD);
		assertColoredText(message, "ReceiverPlayer", ChatFormatting.AQUA);
		assertColoredText(message, "ENCHANTED DIAMOND", ChatFormatting.YELLOW);
		assertColoredText(message, "coins", ChatFormatting.GREEN);
		assertTrue(!message.getString().contains("Receiver starts clear"));
	}

	@Test
	void addsSwitchAndCancelButtonsForPairedReceiverWithoutRun() {
		Component message = TransferChatComponents.forMessage(
			"AutoAuction transfer paired. You are receiver for ENCHANTED DIAMOND with SenderPlayer. Start clear of this item and do not hold the transfer coins; wait for the sender to run /mf run <target>."
		);

		assertTrue(!message.getString().contains(" [Run]"));
		assertTrue(message.getString().contains(" [Switch]"));
		assertTrue(message.getString().contains(" [Cancel]"));
		assertEquals("/mf switch", clickCommand(message, "Switch"));
		assertColoredText(message, "receiver", ChatFormatting.AQUA);
		assertColoredText(message, "SenderPlayer", ChatFormatting.GOLD);
		assertColoredText(message, "ENCHANTED DIAMOND", ChatFormatting.YELLOW);
		assertColoredText(message, "coins", ChatFormatting.GREEN);
		assertTrue(!message.getString().contains("Keep the transfer items"));
	}

	private static Component clickableSibling(Component component, String label) {
		Component match = findClickable(component, label);
		if (match != null) {
			return match;
		}
		throw new AssertionError("No clickable sibling for " + label + " in " + component.getString());
	}

	private static Component findClickable(Component component, String label) {
		if (component.getString().contains(label) && component.getStyle().getClickEvent() != null) {
			return component;
		}
		for (Component sibling : component.getSiblings()) {
			Component match = findClickable(sibling, label);
			if (match != null) {
				return match;
			}
		}
		return null;
	}

	private static ClickEvent clickEvent(Component component, String label) {
		return clickableSibling(component, label).getStyle().getClickEvent();
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

	private static void assertHoverText(Component component, String label, String text, ChatFormatting color) {
		HoverEvent hover = clickableSibling(component, label).getStyle().getHoverEvent();
		assertInstanceOf(HoverEvent.ShowText.class, hover);
		HoverEvent.ShowText showText = (HoverEvent.ShowText) hover;
		assertEquals(text, showText.value().getString());
		assertEquals(TextColor.fromLegacyFormat(color), showText.value().getStyle().getColor());
	}

	private static void assertColoredText(Component component, String text, ChatFormatting color) {
		Component match = findText(component, text, TextColor.fromLegacyFormat(color));
		if (match == null) {
			throw new AssertionError("No " + color + " component containing " + text + " in " + component.getString());
		}
	}

	private static Component findText(Component component, String text, TextColor color) {
		if (component.getString().contains(text) && color.equals(component.getStyle().getColor())) {
			return component;
		}
		for (Component sibling : component.getSiblings()) {
			Component match = findText(sibling, text, color);
			if (match != null) {
				return match;
			}
		}
		return null;
	}
}
