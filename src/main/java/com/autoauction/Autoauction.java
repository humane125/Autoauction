package com.autoauction;

import com.autoauction.update.ModReleaseUpdater;
import com.autoauction.update.ModUpdateConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Autoauction implements ModInitializer {
	public static final String MOD_ID = "autoauction";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("AutoAuction loaded with prelaunch updater support");
		ModUpdateConfig config = ModUpdateConfig.load(FabricLoader.getInstance().getConfigDir());
		ModReleaseUpdater.checkForUpdateAsync(
			MOD_ID,
			"AutoAuction",
			config.apiBaseUrl(),
			config.apiToken(),
			Autoauction::exitAfterUpdateStaged,
			LOGGER::info,
			LOGGER::warn
		);
	}

	private static void exitAfterUpdateStaged() {
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(2_000L);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			System.exit(0);
		}, "AutoAuction Update Exit");
		thread.setDaemon(false);
		thread.start();
	}
}
