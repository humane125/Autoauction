package com.autoauction.client.notify;

import com.autoauction.client.config.AutoAuctionConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordNotifierTest {
	@Test
	void includesMentionInIssueMessages() throws Exception {
		AtomicReference<String> body = new AtomicReference<>("");
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/webhook", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes()));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();

		try {
			String webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
			AutoAuctionConfig config = new AutoAuctionConfig("https://lazy-similarly-reaffirm.ngrok-free.dev", "",
				webhook, "123456789012345678", "/stopmacro", "/hub", true, 25_000, 8_000, 250, 5_000);

			new DiscordNotifier(config).issue("failed to open Auction House");

			assertTrue(body.get().contains("<@123456789012345678>"));
			assertTrue(body.get().contains("failed to open Auction House"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void sendsBanAlertWithMentionUsernameAndReason() throws Exception {
		AtomicReference<String> body = new AtomicReference<>("");
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/webhook", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes()));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();

		try {
			String webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
			AutoAuctionConfig config = new AutoAuctionConfig("https://lazy-similarly-reaffirm.ngrok-free.dev", "",
				webhook, "123132", "/stopmacro", "/hub", true, 25_000, 8_000, 250, 5_000);

			new DiscordNotifier(config).ban("RobinRz", "Cheating");

			assertTrue(body.get().contains("<@123132> RobinRz has been banned"));
			assertTrue(body.get().contains("Reason: Cheating"));
			assertFalse(body.get().contains("AutoAuction issue"));
		} finally {
			server.stop(0);
		}
	}
}
