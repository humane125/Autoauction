package com.autoauction.client.api;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.AuctionItemRequest;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class AuctionApiClient {
	private static final Gson GSON = new Gson();

	private final AutoAuctionConfig config;
	private final HttpClient client = HttpClient.newHttpClient();

	public AuctionApiClient(AutoAuctionConfig config) {
		this.config = config;
	}

	public RecommendResponse recommend(AuctionItemRequest requestBody) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(config.apiBaseUrl().replaceAll("/+$", "") + "/api/recommend-bin"))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

		if (!config.apiToken().isBlank()) {
			builder.header("Authorization", "Bearer " + config.apiToken());
		}

		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Auction API returned HTTP " + response.statusCode());
		}

		return GSON.fromJson(response.body(), RecommendResponse.class);
	}

	public record RecommendResponse(int recommendedPrice, List<String> warnings) {
	}
}
