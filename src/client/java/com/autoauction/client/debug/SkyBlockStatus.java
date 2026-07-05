package com.autoauction.client.debug;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SkyBlockStatus(Optional<String> area, Optional<Island> currentIsland, OptionalLong purse) {
	private static final Pattern PURSE_PATTERN = Pattern.compile("(?i)\\bpurse:\\s*([0-9,]+)");

	public enum Island {
		HUB("Hub"),
		PRIVATE_ISLAND("Private Island");

		private final String displayName;

		Island(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	public static SkyBlockStatus fromLines(List<String> scoreboardLines, List<String> tabListLines) {
		Optional<String> area = parseArea(tabListLines);
		Optional<Island> island = area.flatMap(SkyBlockStatus::knownIsland);
		OptionalLong purse = parsePurse(scoreboardLines, tabListLines);
		return new SkyBlockStatus(area, island, purse);
	}

	public static boolean hasSkyBlockScoreboard(List<String> scoreboardLines) {
		for (String line : safeLines(scoreboardLines)) {
			String clean = cleanLine(line);
			if (clean.regionMatches(true, 0, "title=", 0, "title=".length())) {
				clean = clean.substring("title=".length()).trim();
			}
			if (clean.equalsIgnoreCase("SKYBLOCK")) {
				return true;
			}
		}
		return false;
	}

	public static String cleanLine(String line) {
		return String.valueOf(line == null ? "" : line)
			.replaceAll("\u00a7.", "")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static Optional<String> parseArea(List<String> tabListLines) {
		for (String line : safeLines(tabListLines)) {
			String clean = cleanLine(line);
			if (clean.regionMatches(true, 0, "Area:", 0, "Area:".length())) {
				String area = clean.substring("Area:".length()).trim();
				if (!area.isBlank()) {
					return Optional.of(area);
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<Island> knownIsland(String area) {
		String clean = cleanLine(area).toLowerCase(Locale.ROOT);
		if (clean.equals("hub")) {
			return Optional.of(Island.HUB);
		}
		if (clean.equals("private island")) {
			return Optional.of(Island.PRIVATE_ISLAND);
		}
		return Optional.empty();
	}

	private static OptionalLong parsePurse(List<String> scoreboardLines, List<String> tabListLines) {
		OptionalLong scoreboardPurse = parsePurse(scoreboardLines);
		return scoreboardPurse.isPresent() ? scoreboardPurse : parsePurse(tabListLines);
	}

	private static OptionalLong parsePurse(List<String> lines) {
		for (String line : safeLines(lines)) {
			Matcher matcher = PURSE_PATTERN.matcher(cleanLine(line));
			if (matcher.find()) {
				try {
					return OptionalLong.of(Long.parseLong(matcher.group(1).replace(",", "")));
				} catch (NumberFormatException ignored) {
					return OptionalLong.empty();
				}
			}
		}
		return OptionalLong.empty();
	}

	private static List<String> safeLines(List<String> lines) {
		return lines == null ? List.of() : lines;
	}
}
