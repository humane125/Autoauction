package com.autoauction.client.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuctionItemRequestFactory {
	private static final int RECOMMENDATION_KILL_RANGE = 5_000;

	public AuctionItemRequest fromArmor(ArmorSnapshot armor, int killThreshold) {
		int maxKills = killThreshold + RECOMMENDATION_KILL_RANGE;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("minKills", killThreshold);
		attributes.put("maxKills", maxKills);
		attributes.put("kills", killThreshold + (RECOMMENDATION_KILL_RANGE / 2));
		attributes.put("recomb", armor.recomb());
		attributes.put("minStars", armor.stars());
		return new AuctionItemRequest(armor.baseName(), attributes);
	}
}
