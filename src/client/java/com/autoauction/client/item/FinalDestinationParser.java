package com.autoauction.client.item;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FinalDestinationParser {
	private static final Pattern KILLS = Pattern.compile("(?i)kills\\s*:\\s*([0-9,]+)");
	private static final Pattern NEXT_UPGRADE_PROGRESS = Pattern.compile("(?i)next\\s+upgrade\\s*:.*\\(([0-9,]+)\\s*/\\s*[0-9,]+\\)");
	private static final Pattern LEGACY_FORMATTING = Pattern.compile("(?i)\u00a7[0-9a-fk-or]");

	public Optional<ArmorSnapshot> parse(ArmorPiece piece, String displayName, List<String> loreLines) {
		String cleanDisplayName = stripFormatting(displayName);
		List<String> cleanLoreLines = loreLines.stream().map(FinalDestinationParser::stripFormatting).toList();
		if (!cleanDisplayName.contains(piece.baseName())) {
			return Optional.empty();
		}

		int kills = firstNumber(cleanLoreLines, KILLS)
			.orElseGet(() -> firstNumber(cleanLoreLines, NEXT_UPGRADE_PROGRESS).orElse(0));

		boolean recomb = cleanLoreLines.stream().anyMatch(line -> line.toLowerCase().contains("recombobulated"));
		return Optional.of(new ArmorSnapshot(piece, cleanDisplayName, piece.baseName(), kills, recomb, countStars(cleanDisplayName)));
	}

	private static String stripFormatting(String value) {
		return LEGACY_FORMATTING.matcher(String.valueOf(value == null ? "" : value)).replaceAll("");
	}

	private static Optional<Integer> firstNumber(List<String> lines, Pattern pattern) {
		return lines.stream()
			.map(pattern::matcher)
			.filter(matcher -> matcher.find())
			.map(matcher -> matcher.group(1).replace(",", ""))
			.map(Integer::parseInt)
			.findFirst();
	}

	private int countStars(String displayName) {
		int stars = 0;
		for (int i = 0; i < displayName.length(); i++) {
			if (displayName.charAt(i) == '\u272a') {
				stars++;
			}
		}
		return stars;
	}
}
