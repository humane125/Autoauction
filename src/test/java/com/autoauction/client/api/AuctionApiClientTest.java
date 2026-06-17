package com.autoauction.client.api;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.AuctionItemRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionApiClientTest {
	@Test
	void postsToConfiguredRecommendBinEndpoint() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/recommend-bin", exchange -> {
			byte[] response = "{\"recommendedPrice\":25000000,\"warnings\":[]}".getBytes();
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			AutoAuctionConfig config = new AutoAuctionConfig(baseUrl, "", "", "", "/stopmacro", "/hub", true,
				25_000, 8_000, 250, 5_000);
			AuctionApiClient client = new AuctionApiClient(config);

			var response = client.recommend(new AuctionItemRequest("Final Destination Chestplate", Map.of("minKills", 25_000)));

			assertEquals(25_000_000, response.recommendedPrice());
		} finally {
			server.stop(0);
		}
	}
}
