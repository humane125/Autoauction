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
	private static final Pattern PAIRED = Pattern.compile("^AutoAuction transfer paired\\. You are (sender|receiver)\\. Sender: ([^.]+)\\. Receiver: ([^.]+)\\. Item: (.+)\\. Sender holds the transfer items and coins\\. Receiver starts clear of this item and does not hold the transfer coins\\. Sender runs /mf run <target> when both accounts are ready\\.$");

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

		Matcher paired = PAIRED.matcher(text);
		if (paired.matches()) {
			String role = paired.group(1);
			component = pairedComponent(role, paired.group(2), paired.group(3), paired.group(4));
			if ("sender".equalsIgnoreCase(role)) {
				component.append(buttonInline("Run", ChatFormatting.GOLD, new ClickEvent.SuggestCommand("/mf run "),
					"Click to fill /mf run <target>", ChatFormatting.AQUA));
			}
			component.append(buttonInline("Switch", ChatFormatting.LIGHT_PURPLE, new ClickEvent.RunCommand("/mf switch"),
				"Switch sender and receiver roles", ChatFormatting.LIGHT_PURPLE));
			return component.append(buttonInline("Cancel", ChatFormatting.RED, new ClickEvent.RunCommand("/mf cancel"),
				"Cancel transfer session", ChatFormatting.RED));
		}

		return component;
	}

	private static MutableComponent pairedComponent(String role, String sender, String receiver, String itemName) {
		MutableComponent component = Component.literal("AutoAuction transfer paired. You are ");
		appendColored(component, role.toLowerCase(), roleColor(role), true);
		component.append(Component.literal(". Sender: "));
		appendColored(component, sender, ChatFormatting.GOLD, true);
		component.append(Component.literal(". Receiver: "));
		appendColored(component, receiver, ChatFormatting.AQUA, true);
		component.append(Component.literal(". Item: "));
		appendColored(component, itemName, ChatFormatting.YELLOW, true);
		component.append(Component.literal(". "));
		appendColored(component, "Sender", ChatFormatting.GOLD, true);
		component.append(Component.literal(" holds the transfer "));
		appendColored(component, "items", ChatFormatting.YELLOW, true);
		component.append(Component.literal(" and "));
		appendColored(component, "coins", ChatFormatting.GREEN, true);
		component.append(Component.literal(". "));
		appendColored(component, "Receiver", ChatFormatting.AQUA, true);
		component.append(Component.literal(" starts clear of this item and does not hold the transfer "));
		appendColored(component, "coins", ChatFormatting.GREEN, true);
		component.append(Component.literal(". "));
		appendColored(component, "Sender", ChatFormatting.GOLD, true);
		component.append(Component.literal(" runs "));
		appendColored(component, "/mf run <target>", ChatFormatting.GOLD, true);
		component.append(Component.literal(" when both accounts are ready."));
		return component;
	}

	private static ChatFormatting roleColor(String role) {
		return "receiver".equalsIgnoreCase(role) ? ChatFormatting.AQUA : ChatFormatting.GOLD;
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
