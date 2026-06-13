package com.autoauction.client.config;

import java.util.List;

public record AutoAuctionConfig(
	String apiBaseUrl,
	String apiToken,
	String discordWebhookUrl,
	String discordMentionUserId,
	String macroStopCommand,
	String returnToHubCommand,
	boolean enabledByDefault,
	boolean dryRun,
	boolean privateServerOnly,
	List<String> allowedServers,
	int killThreshold,
	int minBinPrice,
	int maxBinPrice,
	int screenTimeoutMs,
	int clickDelayMs,
	int disconnectDelayMs
) {
	public static AutoAuctionConfig defaults() {
		return new AutoAuctionConfig(
			"https://lazy-similarly-reaffirm.ngrok-free.dev",
			"",
			"",
			"",
			"",
			"/hub",
			false,
			true,
			true,
			List.of("localhost", "127.0.0.1"),
			25_000,
			1_000_000,
			30_000_000,
			8_000,
			750,
			5_000
		);
	}

	public boolean canRunRealListing() {
		return dryRun || !macroStopCommand.isBlank();
	}

	public AutoAuctionConfig withDryRun(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			returnToHubCommand, enabledByDefault, value, privateServerOnly, allowedServers, killThreshold, minBinPrice, maxBinPrice,
			screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withMacroStopCommand(String value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, value,
			returnToHubCommand, enabledByDefault, dryRun, privateServerOnly, allowedServers, killThreshold, minBinPrice, maxBinPrice,
			screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withEnabledByDefault(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			returnToHubCommand, value, dryRun, privateServerOnly, allowedServers, killThreshold, minBinPrice, maxBinPrice,
			screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}
}
