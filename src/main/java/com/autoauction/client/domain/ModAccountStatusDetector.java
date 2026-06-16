package com.autoauction.client.domain;

import net.minecraft.network.DisconnectionDetails;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModAccountStatusDetector {
	private static final Pattern TEMPORARY_BAN_PATTERN = Pattern.compile("temporarily\\s+banned\\s+for\\s+(.+?)\\s+from\\s+this\\s+server", Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION_PART_PATTERN = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
	private static final Pattern REASON_PATTERN = Pattern.compile("(?im)^\\s*Reason:\\s*(.+?)\\s*$");
	private static final Pattern BAN_ID_PATTERN = Pattern.compile("(?im)^\\s*Ban\\s+ID:\\s*(#[A-Za-z0-9]+)\\s*$");

	private ModAccountStatusDetector() {
	}

	public static boolean isHypixelServer(String address) {
		return String.valueOf(address == null ? "" : address)
			.toLowerCase(Locale.ROOT)
			.contains("hypixel.net");
	}

	public static boolean isHypixelBanScreen(String text) {
		String normalized = String.valueOf(text == null ? "" : text).toLowerCase(Locale.ROOT);
		if (!normalized.contains("ban")) {
			return false;
		}
		return normalized.contains("temporarily banned")
			|| normalized.contains("permanently banned")
			|| normalized.contains("ban id")
			|| normalized.contains("hypixel.net/appeal");
	}

	public static boolean isHypixelBanScreen(DisconnectionDetails details) {
		if (details == null) {
			return false;
		}
		return isHypixelBanScreen(details.reason().getString());
	}

	public static BanDetails parseHypixelBanDetails(DisconnectionDetails details, Instant capturedAt) {
		if (details == null) {
			return BanDetails.notBanned();
		}
		return parseHypixelBanDetails(details.reason().getString(), capturedAt);
	}

	public static BanDetails parseHypixelBanDetails(String text, Instant capturedAt) {
		String rawText = String.valueOf(text == null ? "" : text);
		if (!isHypixelBanScreen(rawText)) {
			return BanDetails.notBanned();
		}

		long durationMs = parseDurationMs(rawText);
		String banUntil = durationMs > 0
			? capturedAt.plusMillis(durationMs).toString()
			: null;
		return new BanDetails(
			true,
			firstMatch(REASON_PATTERN, rawText),
			firstMatch(BAN_ID_PATTERN, rawText),
			durationMs,
			banUntil
		);
	}

	private static long parseDurationMs(String text) {
		Matcher temporaryBan = TEMPORARY_BAN_PATTERN.matcher(text);
		if (!temporaryBan.find()) {
			return 0L;
		}

		Matcher part = DURATION_PART_PATTERN.matcher(temporaryBan.group(1));
		Duration duration = Duration.ZERO;
		while (part.find()) {
			long amount = Long.parseLong(part.group(1));
			switch (part.group(2).toLowerCase(Locale.ROOT)) {
				case "d" -> duration = duration.plusDays(amount);
				case "h" -> duration = duration.plusHours(amount);
				case "m" -> duration = duration.plusMinutes(amount);
				case "s" -> duration = duration.plusSeconds(amount);
				default -> {
				}
			}
		}
		return duration.toMillis();
	}

	private static String firstMatch(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		String value = matcher.group(1).trim();
		return value.isBlank() ? null : value;
	}

	public record BanDetails(boolean isBanned, String reason, String banId, long durationMs, String banUntil) {
		private static BanDetails notBanned() {
			return new BanDetails(false, null, null, 0L, null);
		}
	}
}
