package com.autoauction.client.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuctionItemRequestFactory {
	public AuctionItemRequest fromArmor(ArmorSnapshot armor, int killThreshold) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("minKills", killThreshold);
		attributes.put("maxKills", armor.kills());
		attributes.put("recomb", armor.recomb());
		attributes.put("minStars", armor.stars());
		return new AuctionItemRequest(armor.baseName(), attributes);
	}
}
