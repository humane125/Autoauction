package com.autoauction.client.domain;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModAccountStatusDetectorTest {
	@Test
	void detectsHypixelServerAddresses() {
		assertTrue(ModAccountStatusDetector.isHypixelServer("mc.hypixel.net"));
		assertTrue(ModAccountStatusDetector.isHypixelServer("hypixel.net"));
		assertTrue(ModAccountStatusDetector.isHypixelServer("play.hypixel.net:25565"));
		assertFalse(ModAccountStatusDetector.isHypixelServer("localhost"));
		assertFalse(ModAccountStatusDetector.isHypixelServer(null));
	}

	@Test
	void detectsHypixelBanScreenText() {
		assertTrue(ModAccountStatusDetector.isHypixelBanScreen("""
			Failed to connect to the server
			You are temporarily banned for 29d 6h 14m from this server!
			Find out more: https://www.hypixel.net/appeal
			Ban ID: #01346337
			"""));
		assertTrue(ModAccountStatusDetector.isHypixelBanScreen("You are permanently banned from this server! Ban ID: #123"));
		assertFalse(ModAccountStatusDetector.isHypixelBanScreen("Disconnected from server"));
	}

	@Test
	void detectsHypixelBanDisconnectionDetails() {
		DisconnectionDetails details = new DisconnectionDetails(Component.literal("""
			You are temporarily banned for 29d 6h 14m from this server!
			Reason: Cheating through the use of unfair game advantages.
			Find out more: https://www.hypixel.net/appeal
			Ban ID: #01346337
			"""));

		assertTrue(ModAccountStatusDetector.isHypixelBanScreen(details));
	}

	@Test
	void parsesHypixelTemporaryBanDetails() {
		ModAccountStatusDetector.BanDetails details = ModAccountStatusDetector.parseHypixelBanDetails("""
			Failed to connect to the server
			You are temporarily banned for 29d 5h 46m 2s from this server!
			Reason: Cheating through the use of unfair game advantages.
			Find out more: https://www.hypixel.net/appeal
			Ban ID: #01346337
			Sharing your Ban ID may affect the processing of your appeal!
			""", Instant.parse("2026-06-15T15:00:00Z"));

		assertTrue(details.isBanned());
		assertEquals("Cheating through the use of unfair game advantages.", details.reason());
		assertEquals("#01346337", details.banId());
		assertEquals(2526362000L, details.durationMs());
		assertEquals("2026-07-14T20:46:02Z", details.banUntil());
	}
}
