package com.autoauction.client.config;

public record AutoAuctionConfig(
	String apiBaseUrl,
	String apiToken,
	String discordWebhookUrl,
	String discordMentionUserId,
	String macroStopCommand,
	String macroStartCommand,
	String nebulaMacroToggleKey,
	String returnToHubCommand,
	boolean enabledByDefault,
	boolean debugEnabled,
	int killThreshold,
	int screenTimeoutMs,
	int clickDelayMs,
	int disconnectDelayMs
) {
	public AutoAuctionConfig {
		apiBaseUrl = valueOrEmpty(apiBaseUrl);
		apiToken = valueOrEmpty(apiToken);
		discordWebhookUrl = valueOrEmpty(discordWebhookUrl);
		discordMentionUserId = valueOrEmpty(discordMentionUserId);
		macroStopCommand = valueOrEmpty(macroStopCommand);
		macroStartCommand = valueOrEmpty(macroStartCommand);
		nebulaMacroToggleKey = valueOrDefault(nebulaMacroToggleKey, "P");
		returnToHubCommand = valueOrEmpty(returnToHubCommand);
	}

	public static AutoAuctionConfig defaults() {
		return new AutoAuctionConfig(
			"https://lazy-similarly-reaffirm.ngrok-free.dev",
			"",
			"",
			"",
			"",
			"",
			"P",
			"/hub",
			true,
			false,
			25_000,
			8_000,
			750,
			5_000
		);
	}

	public boolean canRunRealListing() {
		return true;
	}

	public AutoAuctionConfig withMacroStopCommand(String value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, value, macroStartCommand, nebulaMacroToggleKey,
			returnToHubCommand, enabledByDefault, debugEnabled, killThreshold, screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withEnabledByDefault(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			macroStartCommand, nebulaMacroToggleKey, returnToHubCommand, value, debugEnabled, killThreshold, screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	public AutoAuctionConfig withDebugEnabled(boolean value) {
		return new AutoAuctionConfig(apiBaseUrl, apiToken, discordWebhookUrl, discordMentionUserId, macroStopCommand,
			macroStartCommand, nebulaMacroToggleKey, returnToHubCommand, enabledByDefault, value, killThreshold, screenTimeoutMs, clickDelayMs, disconnectDelayMs);
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String valueOrDefault(String value, String defaultValue) {
		String resolved = valueOrEmpty(value).trim();
		return resolved.isEmpty() ? defaultValue : resolved;
	}
}
