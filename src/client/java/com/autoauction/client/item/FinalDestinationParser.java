package com.autoauction.client.item;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FinalDestinationParser {
	private static final Pattern KILLS = Pattern.compile("(?i)kills\\s*:\\s*([0-9,]+)");

	public Optional<ArmorSnapshot> parse(ArmorPiece piece, String displayName, List<String> loreLines) {
		if (!displayName.contains(piece.baseName())) {
			return Optional.empty();
		}

		int kills = loreLines.stream()
			.map(KILLS::matcher)
			.filter(matcher -> matcher.find())
			.map(matcher -> matcher.group(1).replace(",", ""))
			.mapToInt(Integer::parseInt)
			.findFirst()
			.orElse(0);

		boolean recomb = loreLines.stream().anyMatch(line -> line.toLowerCase().contains("recombobulated"));
		return Optional.of(new ArmorSnapshot(piece, displayName, piece.baseName(), kills, recomb, countStars(displayName)));
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
