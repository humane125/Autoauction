package com.autoauction.client.notify;

import com.autoauction.client.config.AutoAuctionConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class DiscordNotifier {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final AutoAuctionConfig config;
	private final HttpClient client = HttpClient.newHttpClient();

	public DiscordNotifier(AutoAuctionConfig config) {
		this.config = config;
	}

	public void issue(String message) throws Exception {
		send("issue", mentionPrefix() + "AutoAuction issue: " + message);
	}

	public void info(String message) throws Exception {
		send("info", "AutoAuction: " + message);
	}

	private String mentionPrefix() {
		return config.discordMentionUserId().isBlank() ? "" : "<@" + config.discordMentionUserId() + "> ";
	}

	private void send(String type, String content) throws Exception {
		if (config.discordWebhookUrl().isBlank()) {
			return;
		}

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(config.discordWebhookUrl()))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("content", content))))
			.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Discord webhook failed for " + type + " with HTTP " + response.statusCode());
		}
	}
}
