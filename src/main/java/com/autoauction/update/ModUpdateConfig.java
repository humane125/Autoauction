package com.autoauction.update;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

public record ModUpdateConfig(String apiBaseUrl, String apiToken) {
	private static final Gson GSON = new Gson();

	public ModUpdateConfig {
		apiBaseUrl = clean(apiBaseUrl);
		apiToken = clean(apiToken);
	}

	public static ModUpdateConfig load(Path configDir) {
		Path configFile = configDir.resolve("autoauction.json");
		if (Files.notExists(configFile)) {
			return new ModUpdateConfig("", "");
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
			return GSON.fromJson(json, ModUpdateConfig.class);
		} catch (Exception ignored) {
			return new ModUpdateConfig("", "");
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}
}
