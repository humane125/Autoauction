package com.autoauction.client.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TransferChatComponents {
	private static final Pattern ACCOUNT_LINE = Pattern.compile("^-\\s+([^\\s]+)\\s+\\(([^)]*)\\)$");
	private static final Pattern INCOMING_INVITE = Pattern.compile("^AutoAuction transfer invite from ([^\\s]+) for .+");
	private static final Pattern PENDING_INVITE = Pattern.compile("^AutoAuction transfer invite sent to ([^\\s]+) for .+");
	private static final Pattern PAIRED_SENDER = Pattern.compile("^AutoAuction transfer paired\\. You are sender for (.+) with ([^.]+)\\. Keep the transfer items and coins on this account, then run /mf run <target> when ready\\.$");
	private static final Pattern PAIRED_RECEIVER = Pattern.compile("^AutoAuction transfer paired\\. You are receiver for (.+) with ([^.]+)\\. Start clear of this item and do not hold the transfer coins; wait for the sender to run /mf run <target>\\.$");

	private TransferChatComponents() {
	}

	public static Component forMessage(String message) {
		String text = String.valueOf(message == null ? "" : message);
		MutableComponent component = Component.literal(text);

		Matcher account = ACCOUNT_LINE.matcher(text);
		if (account.matches()) {
			return component.append(buttonInline("Pair", ChatFormatting.GOLD,
				new ClickEvent.SuggestCommand("/mf pair " + account.group(1) + " "),
				"Click to fill /mf pair " + account.group(1) + " <item>", ChatFormatting.AQUA));
		}

		Matcher incoming = INCOMING_INVITE.matcher(text);
		if (incoming.matches()) {
			String sender = incoming.group(1);
			return component
				.append(buttonInline("Accept", ChatFormatting.GREEN, new ClickEvent.RunCommand("/mf accept " + sender),
					"Accept " + sender + "'s transfer invite", ChatFormatting.GREEN))
				.append(buttonInline("Decline", ChatFormatting.RED, new ClickEvent.RunCommand("/mf decline " + sender),
					"Decline " + sender + "'s transfer invite", ChatFormatting.RED));
		}

		Matcher pending = PENDING_INVITE.matcher(text);
		if (pending.matches()) {
			return component.append(buttonInline("Cancel", ChatFormatting.RED, new ClickEvent.RunCommand("/mf cancel"),
				"Cancel pending transfer", ChatFormatting.RED));
		}

		Matcher pairedSender = PAIRED_SENDER.matcher(text);
		if (pairedSender.matches()) {
			component = pairedSenderComponent(pairedSender.group(1), pairedSender.group(2));
			component.append(buttonInline("Run", ChatFormatting.GOLD, new ClickEvent.SuggestCommand("/mf run "),
				"Click to fill /mf run <target>", ChatFormatting.AQUA));
			component.append(buttonInline("Switch", ChatFormatting.LIGHT_PURPLE, new ClickEvent.RunCommand("/mf switch"),
				"Switch sender and receiver roles", ChatFormatting.LIGHT_PURPLE));
			return component.append(buttonInline("Cancel", ChatFormatting.RED, new ClickEvent.RunCommand("/mf cancel"),
				"Cancel transfer session", ChatFormatting.RED));
		}

		Matcher pairedReceiver = PAIRED_RECEIVER.matcher(text);
		if (pairedReceiver.matches()) {
			component = pairedReceiverComponent(pairedReceiver.group(1), pairedReceiver.group(2));
			component.append(buttonInline("Switch", ChatFormatting.LIGHT_PURPLE, new ClickEvent.RunCommand("/mf switch"),
				"Switch sender and receiver roles", ChatFormatting.LIGHT_PURPLE));
			return component.append(buttonInline("Cancel", ChatFormatting.RED, new ClickEvent.RunCommand("/mf cancel"),
				"Cancel transfer session", ChatFormatting.RED));
		}

		return component;
	}

	private static MutableComponent pairedSenderComponent(String itemName, String receiver) {
		MutableComponent component = Component.literal("AutoAuction transfer paired. You are ");
		appendColored(component, "sender", ChatFormatting.GOLD, true);
		component.append(Component.literal(" for "));
		appendColored(component, itemName, ChatFormatting.YELLOW, true);
		component.append(Component.literal(" with "));
		appendColored(component, receiver, ChatFormatting.AQUA, true);
		component.append(Component.literal(". Keep the transfer "));
		appendColored(component, "items", ChatFormatting.YELLOW, true);
		component.append(Component.literal(" and "));
		appendColored(component, "coins", ChatFormatting.GREEN, true);
		component.append(Component.literal(" on this account, then run "));
		appendColored(component, "/mf run <target>", ChatFormatting.GOLD, true);
		component.append(Component.literal(" when ready."));
		return component;
	}

	private static MutableComponent pairedReceiverComponent(String itemName, String sender) {
		MutableComponent component = Component.literal("AutoAuction transfer paired. You are ");
		appendColored(component, "receiver", ChatFormatting.AQUA, true);
		component.append(Component.literal(" for "));
		appendColored(component, itemName, ChatFormatting.YELLOW, true);
		component.append(Component.literal(" with "));
		appendColored(component, sender, ChatFormatting.GOLD, true);
		component.append(Component.literal(". Start clear of this item and do not hold the transfer "));
		appendColored(component, "coins", ChatFormatting.GREEN, true);
		component.append(Component.literal("; wait for the sender to run "));
		appendColored(component, "/mf run <target>", ChatFormatting.GOLD, true);
		component.append(Component.literal("."));
		return component;
	}

	private static void appendColored(MutableComponent component, String text, ChatFormatting color, boolean bold) {
		component.append(Component.literal(text).withStyle(style -> style.withColor(color).withBold(bold)));
	}

	private static MutableComponent buttonInline(String label, ChatFormatting color, ClickEvent clickEvent, String hoverText,
												ChatFormatting hoverColor) {
		return Component.literal(" ").append(button(label, color, clickEvent, hoverText, hoverColor));
	}

	private static MutableComponent button(String label, ChatFormatting color, ClickEvent clickEvent, String hoverText,
										  ChatFormatting hoverColor) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(color)
			.withBold(true)
			.withUnderlined(false)
			.withClickEvent(clickEvent)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText).withStyle(hoverColor))));
	}
}
