package com.autoauction.client.debug;

public final class SkyBlockIslandGuard {
	private SkyBlockIslandGuard() {
	}

	public static boolean needsPrivateIslandWarp(SkyBlockStatus status) {
		return status == null || status.currentIsland()
			.map(island -> island != SkyBlockStatus.Island.PRIVATE_ISLAND)
			.orElse(true);
	}
}
