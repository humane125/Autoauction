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
	private static final Pattern PAIRED = Pattern.compile("^AutoAuction transfer paired as (sender|receiver) with ([^\\s]+) for .+");

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
			if ("sender".equalsIgnoreCase(paired.group(1))) {
				component.append(buttonInline("Run", ChatFormatting.GOLD, new ClickEvent.SuggestCommand("/mf run "),
					"Click to fill /mf run <target>", ChatFormatting.AQUA));
			}
			return component.append(buttonInline("Cancel", ChatFormatting.RED, new ClickEvent.RunCommand("/mf cancel"),
				"Cancel transfer session", ChatFormatting.RED));
		}

		return component;
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
