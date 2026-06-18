package com.autoauction.client.transfer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class HypixelBazaarClient {
	private static final URI BAZAAR_URI = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private HypixelBazaarClient() {
	}

	public static Optional<QuickStatus> fetchQuickStatus(String productId) {
		String cleanProductId = String.valueOf(productId == null ? "" : productId).trim();
		if (cleanProductId.isBlank()) {
			return Optional.empty();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(BAZAAR_URI)
				.timeout(Duration.ofSeconds(15))
				.GET()
				.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return Optional.empty();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!root.has("products") || !root.get("products").isJsonObject()) {
				return Optional.empty();
			}
			JsonObject products = root.getAsJsonObject("products");
			if (!products.has(cleanProductId) || !products.get(cleanProductId).isJsonObject()) {
				return Optional.empty();
			}
			JsonObject product = products.getAsJsonObject(cleanProductId);
			if (!product.has("quick_status") || !product.get("quick_status").isJsonObject()) {
				return Optional.empty();
			}
			JsonObject status = product.getAsJsonObject("quick_status");
			return Optional.of(new QuickStatus(
				stringProperty(status, "productId", cleanProductId),
				doubleProperty(status, "buyPrice"),
				doubleProperty(status, "sellPrice")
			));
		} catch (RuntimeException | java.io.IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		}
	}

	private static String stringProperty(JsonObject object, String name, String fallback) {
		return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback;
	}

	private static double doubleProperty(JsonObject object, String name) {
		return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsDouble() : 0D;
	}

	public record QuickStatus(String productId, double buyPrice, double sellPrice) {
	}
}
