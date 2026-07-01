package com.autoauction.client.stats;

public record AccountStatsSnapshot(
	long purse,
	Integer helmetKills,
	Integer chestplateKills,
	Integer leggingsKills,
	Integer bootsKills,
	boolean macroing
) {
	public AccountStatsSnapshot(
		long purse,
		Integer helmetKills,
		Integer chestplateKills,
		Integer leggingsKills,
		Integer bootsKills
	) {
		this(purse, helmetKills, chestplateKills, leggingsKills, bootsKills, false);
	}
}
