package com.autoauction.client.config;

public record AutoAuctionConfig(
	String apiBaseUrl,
	String apiToken,
	String discordWebhookUrl,
	String discordMentionUserId,
	String macroStopCommand,
	String returnToHubCommand,
	boolean enabledByDefault,
	int killThreshold,
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
			true,
			25_000,
			8_000,
			750,
			5_000
		);
	}

	public boolean canRunRealListing() {
		return !macroStopCommand.isBlank();
	}

	public AutoAuctionConfig withMacroStopCommand(String value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, value,
			returnToHubCommand, enabledByDefault, killThreshold, screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withEnabledByDefault(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			returnToHubCommand, value, killThreshold, screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}
}
