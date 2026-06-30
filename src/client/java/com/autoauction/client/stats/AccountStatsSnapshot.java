package com.autoauction.client.stats;

public record AccountStatsSnapshot(
	long purse,
	int helmetKills,
	int chestplateKills,
	int leggingsKills,
	int bootsKills
) {
}
