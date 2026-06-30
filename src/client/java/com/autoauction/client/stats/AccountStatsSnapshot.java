package com.autoauction.client.stats;

public record AccountStatsSnapshot(
	long purse,
	Integer helmetKills,
	Integer chestplateKills,
	Integer leggingsKills,
	Integer bootsKills
) {
}
