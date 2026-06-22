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
			return component.append(Component.literal(" "))
				.append(button("Pair", new ClickEvent.SuggestCommand("/mf pair " + account.group(1) + " "), "Fill pair command"));
		}

		Matcher incoming = INCOMING_INVITE.matcher(text);
		if (incoming.matches()) {
			String sender = incoming.group(1);
			return component.append(Component.literal(" "))
				.append(button("Accept", new ClickEvent.RunCommand("/mf accept " + sender), "Accept transfer invite"))
				.append(Component.literal(" "))
				.append(button("Decline", new ClickEvent.RunCommand("/mf decline " + sender), "Decline transfer invite"));
		}

		Matcher pending = PENDING_INVITE.matcher(text);
		if (pending.matches()) {
			return component.append(Component.literal(" "))
				.append(button("Cancel", new ClickEvent.RunCommand("/mf cancel"), "Cancel pending transfer"));
		}

		Matcher paired = PAIRED.matcher(text);
		if (paired.matches()) {
			component.append(Component.literal(" "));
			if ("sender".equalsIgnoreCase(paired.group(1))) {
				component.append(button("Run", new ClickEvent.SuggestCommand("/mf run "), "Fill run command"))
					.append(Component.literal(" "));
			}
			return component.append(button("Cancel", new ClickEvent.RunCommand("/mf cancel"), "Cancel transfer session"));
		}

		return component;
	}

	private static MutableComponent button(String label, ClickEvent clickEvent, String hoverText) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withUnderlined(true)
			.withClickEvent(clickEvent)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
	}
}
