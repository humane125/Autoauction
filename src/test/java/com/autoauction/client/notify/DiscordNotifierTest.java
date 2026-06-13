package com.autoauction.client.notify;

import com.autoauction.client.config.AutoAuctionConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
				webhook, "123456789012345678", "/stopmacro", "/hub", false, true, true, List.of("localhost"),
				25_000, 1_000_000, 30_000_000, 8_000, 250, 5_000);

			new DiscordNotifier(config).issue("failed to open Auction House");

			assertTrue(body.get().contains("<@123456789012345678>"));
			assertTrue(body.get().contains("failed to open Auction House"));
		} finally {
			server.stop(0);
		}
	}
}
