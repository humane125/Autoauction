package com.autoauction.client.debug;

import java.util.ArrayList;
import java.util.List;

public final class StatusDumpFormatter {
	private StatusDumpFormatter() {
	}

	public static List<String> format(List<String> scoreboardLines, List<String> tabListLines) {
		List<String> lines = new ArrayList<>();
		lines.add("AutoAuction status dump begin");
		addSection(lines, "scoreboard", scoreboardLines);
		addSection(lines, "tablist", tabListLines);
		lines.add("AutoAuction status dump end");
		return lines;
	}

	private static void addSection(List<String> output, String section, List<String> values) {
		List<String> cleanValues = values == null ? List.of() : values;
		if (cleanValues.isEmpty()) {
			output.add(section + "[empty]");
			return;
		}
		for (int i = 0; i < cleanValues.size(); i++) {
			output.add(section + "[" + i + "] " + cleanValues.get(i));
		}
	}
}
