package com.autoauction.client.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionItemRequestFactoryTest {
	@Test
	void buildsRecommendationRequestFromArmorSnapshot() {
		ArmorSnapshot snapshot = new ArmorSnapshot(
			ArmorPiece.CHESTPLATE,
			"Ancient Final Destination Chestplate",
			"Final Destination Chestplate",
			25_321,
			false,
			0
		);

		AuctionItemRequest request = new AuctionItemRequestFactory().fromArmor(snapshot, 25_000);

		assertEquals("Final Destination Chestplate", request.baseName());
		assertEquals(25_000, request.attributes().get("minKills"));
		assertEquals(30_000, request.attributes().get("maxKills"));
		assertEquals(27_500, request.attributes().get("kills"));
		assertEquals(false, request.attributes().get("recomb"));
		assertEquals(0, request.attributes().get("minStars"));
	}
}
