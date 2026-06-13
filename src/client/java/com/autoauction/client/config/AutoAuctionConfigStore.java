package com.autoauction.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoAuctionConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path configFile;

	public AutoAuctionConfigStore(Path configDir) {
		this.configFile = configDir.resolve("autoauction.json");
	}

	public AutoAuctionConfig load() throws IOException {
		if (!Files.exists(configFile)) {
			AutoAuctionConfig defaults = AutoAuctionConfig.defaults();
			save(defaults);
			return defaults;
		}

		String json = Files.readString(configFile);
		AutoAuctionConfig config = GSON.fromJson(json, AutoAuctionConfig.class);
		return config == null ? AutoAuctionConfig.defaults() : config;
	}

	public void save(AutoAuctionConfig config) throws IOException {
		Files.createDirectories(configFile.getParent());
		Files.writeString(configFile, GSON.toJson(config));
	}
}
