package com.autoauction.client;

import com.autoauction.Autoauction;
import com.autoauction.client.api.AuctionApiClient;
import com.autoauction.client.automation.AuctionAutomationController;
import com.autoauction.client.automation.AutomationState;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.config.AutoAuctionConfigStore;
import com.autoauction.client.control.ModSocketClient;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.domain.AuctionBlockedMessageDetector;
import com.autoauction.client.domain.AuctionItemRequestFactory;
import com.autoauction.client.domain.ModAccountStatusDetector;
import com.autoauction.client.domain.PriceTextFormatter;
import com.autoauction.client.domain.TestArmorSnapshots;
import com.autoauction.client.minecraft.MinecraftGameActions;
import com.autoauction.client.notify.DiscordNotifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class AutoauctionClient implements ClientModInitializer {
	private AutoAuctionConfig config;
	private AuctionAutomationController controller;
	private MinecraftGameActions actions;
	private AuctionApiClient apiClient;
	private ModSocketClient modSocketClient;
	private DiscordNotifier notifier;
	private final AuctionItemRequestFactory requestFactory = new AuctionItemRequestFactory();
	private int tickCounter;
	private boolean workflowStarted;
	private boolean modSocketStarted;
	private boolean dumpSlotsKeyWasDown;
	private boolean emergencyStopKeyWasDown;
	private long lastCookieBuffAlertAt;
	private RealAuctionWorkflow realWorkflow;

	@Override
	public void onInitializeClient() {
		try {
			this.config = new AutoAuctionConfigStore(FabricLoader.getInstance().getConfigDir()).load();
			this.controller = new AuctionAutomationController(config);
			this.actions = new MinecraftGameActions();
			this.apiClient = new AuctionApiClient(config);
			this.modSocketClient = new ModSocketClient(config);
			this.notifier = new DiscordNotifier(config);
			registerCommands();
			registerMessageHandlers();
			registerConnectionStatusHandlers();
			ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
				if (modSocketClient != null) {
					modSocketClient.close();
				}
			});
			ClientTickEvents.END_CLIENT_TICK.register(client -> {
				startModSocketIfNeeded(client);
				reportConnectionStatus(client);
				if (client.player == null || controller == null || actions == null) {
					return;
				}

				handleDumpSlotsHotkey(client);
				handleEmergencyStopHotkey(client);
				if (realWorkflow != null) {
					realWorkflow.tick(client);
				}
				tickCounter++;
				if (tickCounter % 10 == 0) {
					controller.observeArmor(actions.readEquippedFinalDestinationArmor(client));
				}
				if (!workflowStarted && controller.state() == AutomationState.THRESHOLD_REACHED) {
					workflowStarted = true;
					startWorkflow(client, controller.triggeredArmor());
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize AutoAuction client", e);
		}
	}

	private void startModSocketIfNeeded(Minecraft client) {
		if (modSocketStarted || config.apiToken().isBlank()) {
			return;
		}
		String clientVersion = FabricLoader.getInstance().getModContainer("autoauction")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
		modSocketStarted = modSocketClient.start(client.getUser().getName(), clientVersion);
	}

	private void reportConnectionStatus(Minecraft client) {
		if (modSocketClient == null) {
			return;
		}
		String screenText = screenText(client.screen);
		if (ModAccountStatusDetector.isHypixelBanScreen(screenText)) {
			reportModBanStatus(screenText, "screen");
			return;
		}
		if (client.getCurrentServer() != null && ModAccountStatusDetector.isHypixelServer(client.getCurrentServer().ip)) {
			reportModStatus("hypixel", "tick " + client.getCurrentServer().ip);
			return;
		}
		reportModStatus("active", "tick");
	}

	private void registerConnectionStatusHandlers() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData serverData = handler.getServerData();
			if (serverData != null && ModAccountStatusDetector.isHypixelServer(serverData.ip)) {
				reportModStatus("hypixel", "play join " + serverData.ip);
			}
		});
		ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> {
			DisconnectionDetails details = disconnectionDetailsFrom(handler);
			if (ModAccountStatusDetector.isHypixelBanScreen(details)) {
				reportModBanStatus(details, "login disconnect");
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DisconnectionDetails details = connectionDisconnectionDetails(handler.getConnection());
			if (ModAccountStatusDetector.isHypixelBanScreen(details)) {
				reportModBanStatus(details, "play disconnect");
			}
		});
	}

	private void reportModStatus(String status, String source) {
		if (modSocketClient != null && modSocketClient.reportStatus(status)) {
			Autoauction.LOGGER.info("AutoAuction reported account status {} from {}.", status, source);
		}
	}

	private void reportModBanStatus(DisconnectionDetails details, String source) {
		if (details == null) {
			return;
		}
		reportModBanStatus(details.reason().getString(), source);
	}

	private void reportModBanStatus(String text, String source) {
		ModAccountStatusDetector.BanDetails details = ModAccountStatusDetector.parseHypixelBanDetails(text, Instant.now());
		if (details.isBanned() && modSocketClient != null && modSocketClient.reportBan(details)) {
			Autoauction.LOGGER.info(
				"AutoAuction reported account status banned from {}. reason={} banId={} banUntil={}",
				source,
				details.reason(),
				details.banId(),
				details.banUntil()
			);
		}
	}

	private String screenText(Screen screen) {
		if (screen == null) {
			return "";
		}
		StringBuilder text = new StringBuilder(screen.getTitle().getString());
		text.append('\n').append(screen.getNarrationMessage().getString());
		Class<?> type = screen.getClass();
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				try {
					field.setAccessible(true);
					appendScreenFieldText(text, field.get(screen));
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Best-effort screen text extraction; title-only still keeps the game path safe.
				}
			}
			type = type.getSuperclass();
		}
		return text.toString();
	}

	private DisconnectionDetails disconnectionDetailsFrom(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof DisconnectionDetails details) {
			return details;
		}
		if (value instanceof Connection connection) {
			return connectionDisconnectionDetails(connection);
		}
		Class<?> type = value.getClass();
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Object fieldValue = field.get(value);
					if (fieldValue instanceof DisconnectionDetails details) {
						return details;
					}
					if (fieldValue instanceof Connection connection) {
						DisconnectionDetails details = connectionDisconnectionDetails(connection);
						if (details != null) {
							return details;
						}
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Best-effort extraction from Minecraft listener internals.
				}
			}
			type = type.getSuperclass();
		}
		return null;
	}

	private DisconnectionDetails connectionDisconnectionDetails(Connection connection) {
		if (connection == null) {
			return null;
		}
		try {
			return connection.getDisconnectionDetails();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private void appendScreenFieldText(StringBuilder text, Object value) {
		if (value instanceof Component component) {
			text.append('\n').append(component.getString());
		} else if (value instanceof DisconnectionDetails details) {
			appendScreenFieldText(text, details.reason());
		} else if (value instanceof Collection<?> collection) {
			for (Object item : collection) {
				appendScreenFieldText(text, item);
			}
		}
	}

	private void registerMessageHandlers() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (AuctionBlockedMessageDetector.isCookieBuffRequired(message.getString())) {
				handleCookieBuffRequired();
			}
		});
	}

	private void handleCookieBuffRequired() {
		long now = System.currentTimeMillis();
		if (now - lastCookieBuffAlertAt < 30_000) {
			return;
		}
		lastCookieBuffAlertAt = now;
		realWorkflow = null;
		workflowStarted = false;
		controller.stop();
		notifyIssueAsync("Auction House command failed: Booster Cookie Buff is required.");
		Autoauction.LOGGER.warn("AutoAuction stopped because /ah requires Cookie Buff.");
	}

	private void handleDumpSlotsHotkey(Minecraft client) {
		boolean isDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
		if (isDown && !dumpSlotsKeyWasDown) {
			dumpOpenSlots(client);
		}
		dumpSlotsKeyWasDown = isDown;
	}

	private void handleEmergencyStopHotkey(Minecraft client) {
		boolean isDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
		if (isDown && !emergencyStopKeyWasDown) {
			cancelAutomation(client, "F9 emergency stop pressed.");
		}
		emergencyStopKeyWasDown = isDown;
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("autoauction")
			.then(literal("start").executes(context -> {
				controller.start();
				workflowStarted = false;
				sendFeedback(context.getSource(), "AutoAuction watcher started.");
				return 1;
			}))
			.then(literal("stop").executes(context -> {
				controller.stop();
				workflowStarted = false;
				sendFeedback(context.getSource(), "AutoAuction watcher stopped.");
				return 1;
			}))
			.then(literal("status").executes(context -> {
				sendFeedback(context.getSource(), statusMessage());
				return 1;
			}))
			.then(literal("testtrigger").executes(context -> {
				if (!config.dryRun()) {
					sendFeedback(context.getSource(), "AutoAuction testtrigger is blocked unless dryRun is true.");
					return 0;
				}

				workflowStarted = true;
				priceArmorAsync(TestArmorSnapshots.finalDestinationSet(config.killThreshold()),
					"Test trigger ran; priced fake 25k Final Destination set without clicking.");
				sendFeedback(context.getSource(), "AutoAuction testtrigger started API/Discord dry-run pricing.");
				return 1;
			}))
			.then(literal("testlisting").executes(context -> {
				Minecraft client = context.getSource().getClient();
				if (!config.dryRun()) {
					sendFeedback(context.getSource(), "AutoAuction testlisting is blocked unless dryRun is true.");
					return 0;
				}
				if (realWorkflow != null) {
					sendFeedback(context.getSource(), "AutoAuction workflow is already running. Press F9 to cancel it.");
					return 0;
				}
				if (!isAllowedServer(client)) {
					sendFeedback(context.getSource(), "AutoAuction testlisting blocked: current server is not in allowedServers.");
					return 0;
				}

				EnumMap<ArmorPiece, String> equippedNames = actions.readEquippedArmorNames(client);
				if (equippedNames.size() != ArmorPiece.values().length) {
					sendFeedback(context.getSource(), "AutoAuction testlisting needs four equipped armor pieces.");
					return 0;
				}

				startTestListingWorkflow(client, equippedNames);
				sendFeedback(context.getSource(), "AutoAuction testlisting started. It will list your currently equipped armor using fake FD prices. Press F9 to stop.");
				return 1;
			}))
			.then(literal("dumpslots").executes(context -> {
				int slotCount = dumpOpenSlots(context.getSource().getClient());
				sendFeedback(context.getSource(), "AutoAuction dumped " + slotCount + " slots to latest.log.");
				return 1;
			}))
		));
	}

	private int dumpOpenSlots(Minecraft client) {
		var lines = actions.describeOpenContainerSlots(client);
		Autoauction.LOGGER.info("AutoAuction slot dump begin");
		for (String line : lines) {
			Autoauction.LOGGER.info(line);
		}
		Autoauction.LOGGER.info("AutoAuction slot dump end");
		int slotCount = Math.max(0, lines.size() - 1);
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("AutoAuction dumped " + slotCount + " slots to latest.log."));
		}
		return slotCount;
	}

	private String statusMessage() {
		return "AutoAuction status: state=" + controller.state()
			+ ", enabled=" + controller.isEnabled()
			+ ", dryRun=" + config.dryRun()
			+ ", threshold=" + config.killThreshold()
			+ ", apiBaseUrl=" + config.apiBaseUrl();
	}

	private void sendFeedback(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal(message));
	}

	private void cancelAutomation(Minecraft client, String reason) {
		realWorkflow = null;
		workflowStarted = false;
		controller.stop();
		Autoauction.LOGGER.warn("AutoAuction cancelled: {}", reason);
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("AutoAuction cancelled: " + reason));
		}
		notifyIssueAsync("automation cancelled: " + reason);
	}

	private void startWorkflow(Minecraft client, EnumMap<ArmorPiece, ArmorSnapshot> armor) {
		if (config.dryRun()) {
			priceArmorAsync(armor, "Dry run threshold reached; priced armor without clicking.");
			return;
		}

		if (!config.canRunRealListing()) {
			notifyIssueAsync("macroStopCommand is not configured while dryRun is false");
			return;
		}

		if (!isAllowedServer(client)) {
			notifyIssueAsync("current server is not in allowedServers");
			return;
		}

		startRealWorkflow(client, armor);
	}

	private void startRealWorkflow(Minecraft client, EnumMap<ArmorPiece, ArmorSnapshot> armor) {
		CompletableFuture<List<PricedArmor>> prices = priceArmorForRealAsync(armor, defaultInventoryNames(armor));
		realWorkflow = new RealAuctionWorkflow(new ArrayList<>(armor.values()), prices);
		realWorkflow.start(client);
	}

	private void startTestListingWorkflow(Minecraft client, EnumMap<ArmorPiece, String> equippedNames) {
		EnumMap<ArmorPiece, ArmorSnapshot> fakeArmor = TestArmorSnapshots.finalDestinationSet(config.killThreshold());
		CompletableFuture<List<PricedArmor>> prices = priceArmorForRealAsync(fakeArmor, equippedNames);
		realWorkflow = new RealAuctionWorkflow(new ArrayList<>(fakeArmor.values()), prices);
		realWorkflow.start(client);
	}

	private boolean isAllowedServer(Minecraft client) {
		if (!config.privateServerOnly()) {
			return true;
		}
		if (client.getCurrentServer() == null) {
			return false;
		}

		String currentAddress = client.getCurrentServer().ip.toLowerCase();
		return config.allowedServers().stream().map(String::toLowerCase).anyMatch(currentAddress::contains);
	}

	private void priceArmorAsync(EnumMap<ArmorPiece, ArmorSnapshot> armor, String completionMessage) {
		CompletableFuture.runAsync(() -> {
			try {
				for (ArmorSnapshot snapshot : armor.values()) {
					var response = apiClient.recommend(requestFactory.fromArmor(snapshot, config.killThreshold()));
					int price = response.recommendedPrice();
					if (price < config.minBinPrice() || price > config.maxBinPrice()) {
						notifier.issue(snapshot.baseName() + " recommended price " + price + " is outside configured bounds.");
						continue;
					}
					Autoauction.LOGGER.info("AutoAuction priced {} at {}", snapshot.baseName(), price);
				}
				notifier.info(completionMessage);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction pricing workflow failed", e);
				notifyIssueAsync("pricing workflow failed: " + e.getMessage());
			}
		});
	}

	private EnumMap<ArmorPiece, String> defaultInventoryNames(EnumMap<ArmorPiece, ArmorSnapshot> armor) {
		EnumMap<ArmorPiece, String> names = new EnumMap<>(ArmorPiece.class);
		for (Map.Entry<ArmorPiece, ArmorSnapshot> entry : armor.entrySet()) {
			names.put(entry.getKey(), entry.getValue().baseName());
		}
		return names;
	}

	private CompletableFuture<List<PricedArmor>> priceArmorForRealAsync(
		EnumMap<ArmorPiece, ArmorSnapshot> armor,
		EnumMap<ArmorPiece, String> inventoryNames
	) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				List<PricedArmor> priced = new ArrayList<>();
				for (ArmorPiece piece : ArmorPiece.values()) {
					ArmorSnapshot snapshot = armor.get(piece);
					String inventoryName = inventoryNames.get(piece);
					if (snapshot == null || inventoryName == null || inventoryName.isBlank()) {
						throw new IllegalStateException("missing armor test data for " + piece);
					}
					var response = apiClient.recommend(requestFactory.fromArmor(snapshot, config.killThreshold()));
					int price = response.recommendedPrice();
					if (price < config.minBinPrice() || price > config.maxBinPrice()) {
						throw new IllegalStateException(snapshot.baseName() + " recommended price " + price + " is outside configured bounds.");
					}
					Autoauction.LOGGER.info("AutoAuction priced {} at {}", snapshot.baseName(), price);
					priced.add(new PricedArmor(snapshot, inventoryName, price));
				}
				return priced;
			} catch (Exception e) {
				throw new IllegalStateException("pricing workflow failed: " + e.getMessage(), e);
			}
		});
	}

	private void notifyIssueAsync(String message) {
		CompletableFuture.runAsync(() -> {
			try {
				notifier.issue(message);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction Discord notification failed", e);
			}
		});
	}

	private record PricedArmor(ArmorSnapshot armor, String inventoryItemName, int price) {
	}

	private enum RealAuctionState {
		STOP_MACRO,
		OPEN_INVENTORY,
		WAIT_INVENTORY,
		REMOVE_ARMOR,
		WAIT_ARMOR_REMOVED,
		CLOSE_INVENTORY_AFTER_ARMOR,
		WAIT_AFTER_INVENTORY_CLOSE,
		WAIT_PRICES,
		OPEN_AH,
		WAIT_AH,
		CLICK_CREATE_AUCTION,
		WAIT_MANAGE_AUCTIONS,
		CLICK_CREATE_FROM_MANAGE_AUCTIONS,
		WAIT_CREATE_BIN,
		SET_DURATION,
		WAIT_DURATION,
		PICK_TWO_DAYS,
		WAIT_CREATE_BIN_AFTER_DURATION,
		PLACE_ITEM,
		WAIT_ITEM_PLACED,
		OPEN_PRICE_SIGN,
		WAIT_PRICE_SIGN,
		SUBMIT_PRICE_SIGN,
		WAIT_PRICE_APPLIED,
		CREATE_BIN,
		WAIT_CONFIRM,
		CONFIRM,
		NEXT_ITEM,
		RETURN_TO_HUB,
		WAIT_DISCONNECT,
		DONE,
		ERROR
	}

	private final class RealAuctionWorkflow {
		private static final int AH_CREATE_AUCTION_SLOT = 15;
		private static final int MANAGE_AUCTIONS_CREATE_AUCTION_SLOT = 24;
		private static final int CREATE_BIN_ITEM_SLOT = 13;
		private static final int CREATE_BIN_BUTTON_SLOT = 29;
		private static final int CREATE_BIN_PRICE_SLOT = 31;
		private static final int CREATE_BIN_DURATION_SLOT = 33;
		private static final int AUCTION_DURATION_TWO_DAYS_SLOT = 14;
		private static final int CONFIRM_BIN_SLOT = 11;

		private final List<ArmorSnapshot> armor;
		private final CompletableFuture<List<PricedArmor>> pricesFuture;
		private List<PricedArmor> pricedArmor = List.of();
		private int index;
		private int armorRemoveIndex;
		private boolean itemPlaceClicked;
		private RealAuctionState state = RealAuctionState.STOP_MACRO;
		private long nextActionAt;
		private long stateStartedAt;

		private RealAuctionWorkflow(List<ArmorSnapshot> armor, CompletableFuture<List<PricedArmor>> pricesFuture) {
			this.armor = armor;
			this.pricesFuture = pricesFuture;
		}

		private void start(Minecraft client) {
			debug(client, "AutoAuction workflow started.");
			transition(RealAuctionState.STOP_MACRO, client);
		}

		private void tick(Minecraft client) {
			if (state == RealAuctionState.DONE || state == RealAuctionState.ERROR || System.currentTimeMillis() < nextActionAt) {
				return;
			}

			try {
				runState(client);
			} catch (Exception e) {
				fail(client, e.getMessage());
			}
		}

		private void runState(Minecraft client) {
			switch (state) {
				case STOP_MACRO -> {
					debug(client, "Stopping macro with command: " + config.macroStopCommand());
					actions.sendChatCommand(client, config.macroStopCommand());
					transition(RealAuctionState.OPEN_INVENTORY, client);
				}
				case OPEN_INVENTORY -> {
					debug(client, "Opening inventory to remove armor.");
					actions.openInventory(client);
					transition(RealAuctionState.WAIT_INVENTORY, client);
				}
				case WAIT_INVENTORY -> {
					if (!actions.isInventoryOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "inventory did not open before armor removal");
						return;
					}
					transition(RealAuctionState.REMOVE_ARMOR, client);
				}
				case REMOVE_ARMOR -> {
					if (!actions.hasEmptyInventorySlots(client, armor.size())) {
						fail(client, "not enough empty inventory slots to remove armor");
						return;
					}
					if (armorRemoveIndex >= ArmorPiece.values().length) {
						transition(RealAuctionState.CLOSE_INVENTORY_AFTER_ARMOR, client);
						return;
					}

					ArmorPiece piece = ArmorPiece.values()[armorRemoveIndex];
					if (!actions.isArmorPieceEquipped(client, piece)) {
						debug(client, piece + " already empty.");
						armorRemoveIndex++;
						delay();
						return;
					}
					debug(client, "Removing " + piece + " armor slot.");
					actions.quickMoveArmorPieceToInventory(client, piece);
					transition(RealAuctionState.WAIT_ARMOR_REMOVED, client);
				}
				case WAIT_ARMOR_REMOVED -> {
					ArmorPiece piece = ArmorPiece.values()[armorRemoveIndex];
					if (actions.isArmorPieceEquipped(client, piece)) {
						timeout(client, config.screenTimeoutMs(), piece + " was not removed from armor slot");
						return;
					}
					debug(client, piece + " removed.");
					armorRemoveIndex++;
					transition(RealAuctionState.REMOVE_ARMOR, client);
				}
				case CLOSE_INVENTORY_AFTER_ARMOR -> {
					debug(client, "Armor removal complete. Closing inventory before /ah.");
					actions.closeScreen(client);
					transition(RealAuctionState.WAIT_AFTER_INVENTORY_CLOSE, client, 1_000);
				}
				case WAIT_AFTER_INVENTORY_CLOSE -> {
					debug(client, "Inventory closed. Waiting for prices before opening /ah.");
					transition(RealAuctionState.WAIT_PRICES, client);
				}
				case WAIT_PRICES -> {
					if (!pricesFuture.isDone()) {
						timeout(client, 60_000, "pricing API timed out");
						return;
					}
					pricedArmor = pricesFuture.join();
					for (PricedArmor item : pricedArmor) {
						debug(client, "Recommended price for " + item.inventoryItemName() + ": "
							+ PriceTextFormatter.forSign(item.price()) + " (" + item.price() + " coins)");
					}
					transition(RealAuctionState.OPEN_AH, client);
				}
				case OPEN_AH -> {
					debug(client, "Opening /ah for item " + (index + 1) + "/" + pricedArmor.size() + ".");
					actions.sendChatCommand(client, "/ah");
					transition(RealAuctionState.WAIT_AH, client);
				}
				case WAIT_AH -> {
					if (!actions.screenTitleContains(client, "Auction House") && !actions.screenTitleContains(client, "Manage Auctions")) {
						timeout(client, config.screenTimeoutMs(), "Auction House or Manage Auctions screen did not open");
						return;
					}
					transition(RealAuctionState.CLICK_CREATE_AUCTION, client);
				}
				case CLICK_CREATE_AUCTION -> {
					if (actions.screenTitleContains(client, "Manage Auctions")) {
						transition(RealAuctionState.CLICK_CREATE_FROM_MANAGE_AUCTIONS, client);
						return;
					}

					String slotName = actions.itemNameAt(client, AH_CREATE_AUCTION_SLOT).orElse("");
					if (slotName.contains("Manage Auctions")) {
						debug(client, "Auction House shows Manage Auctions first; opening Manage Auctions.");
						actions.clickSlot(client, AH_CREATE_AUCTION_SLOT);
						transition(RealAuctionState.WAIT_MANAGE_AUCTIONS, client);
						return;
					}

					debug(client, "Clicking Create Auction.");
					actions.clickSlot(client, AH_CREATE_AUCTION_SLOT);
					transition(RealAuctionState.WAIT_CREATE_BIN, client);
				}
				case WAIT_MANAGE_AUCTIONS -> {
					if (!actions.screenTitleContains(client, "Manage Auctions")) {
						timeout(client, config.screenTimeoutMs(), "Manage Auctions screen did not open");
						return;
					}
					transition(RealAuctionState.CLICK_CREATE_FROM_MANAGE_AUCTIONS, client);
				}
				case CLICK_CREATE_FROM_MANAGE_AUCTIONS -> {
					debug(client, "Clicking Create Auction from Manage Auctions.");
					actions.clickSlot(client, MANAGE_AUCTIONS_CREATE_AUCTION_SLOT);
					transition(RealAuctionState.WAIT_CREATE_BIN, client);
				}
				case WAIT_CREATE_BIN -> {
					if (actions.screenTitleContains(client, "Create Auction") && !actions.screenTitleContains(client, "Create BIN Auction")) {
						debug(client, "Screen is auction mode; switching to BIN.");
						actions.clickSlot(client, 48);
						delay();
						return;
					}
					if (!actions.screenTitleContains(client, "Create BIN Auction")) {
						timeout(client, config.screenTimeoutMs(), "Create BIN Auction screen did not open");
						return;
					}
					transition(RealAuctionState.SET_DURATION, client);
				}
				case SET_DURATION -> {
					debug(client, "Opening duration selector.");
					actions.clickSlot(client, CREATE_BIN_DURATION_SLOT);
					transition(RealAuctionState.WAIT_DURATION, client);
				}
				case WAIT_DURATION -> {
					if (!actions.screenTitleContains(client, "Auction Duration")) {
						timeout(client, config.screenTimeoutMs(), "Auction Duration screen did not open");
						return;
					}
					transition(RealAuctionState.PICK_TWO_DAYS, client);
				}
				case PICK_TWO_DAYS -> {
					debug(client, "Selecting duration: 2 Days.");
					actions.clickSlot(client, AUCTION_DURATION_TWO_DAYS_SLOT);
					transition(RealAuctionState.WAIT_CREATE_BIN_AFTER_DURATION, client);
				}
				case WAIT_CREATE_BIN_AFTER_DURATION -> {
					if (!actions.screenTitleContains(client, "Create BIN Auction")) {
						timeout(client, config.screenTimeoutMs(), "Create BIN Auction screen did not return after duration selection");
						return;
					}
					transition(RealAuctionState.PLACE_ITEM, client);
				}
				case PLACE_ITEM -> {
					PricedArmor item = currentItem();
					int slot = actions.findHandlerSlotByItemName(client, item.inventoryItemName())
						.orElseThrow(() -> new IllegalStateException("could not find " + item.inventoryItemName() + " in inventory"));
					debug(client, "Placing item: " + item.inventoryItemName() + " from slot " + slot + ".");
					actions.clickSlot(client, slot);
					itemPlaceClicked = true;
					transition(RealAuctionState.WAIT_ITEM_PLACED, client);
				}
				case WAIT_ITEM_PLACED -> {
					String itemName = actions.itemNameAt(client, CREATE_BIN_ITEM_SLOT).orElse("");
					if (itemName.contains(currentItem().inventoryItemName())
						|| (!itemName.isBlank() && !itemName.contains("AUCTION FOR ITEM:"))) {
						itemPlaceClicked = false;
						debug(client, "Item placement detected: slot 13 now shows '" + itemName + "'.");
						transition(RealAuctionState.OPEN_PRICE_SIGN, client);
						return;
					}
					if (System.currentTimeMillis() - stateStartedAt > Math.max(1_000, effectiveClickDelayMs())) {
						Autoauction.LOGGER.warn(
							"AutoAuction continuing after item click; auction slot name is '{}' for expected '{}'",
							itemName,
							currentItem().inventoryItemName()
						);
						itemPlaceClicked = false;
						debug(client, "Continuing after item click; slot 13 still shows '" + itemName + "'.");
						transition(RealAuctionState.OPEN_PRICE_SIGN, client);
						return;
					}
					timeout(client, config.screenTimeoutMs(), "item was not placed into auction slot");
				}
				case OPEN_PRICE_SIGN -> {
					debug(client, "Opening price sign for " + currentItem().inventoryItemName() + ".");
					actions.clickSlot(client, CREATE_BIN_PRICE_SLOT);
					transition(RealAuctionState.WAIT_PRICE_SIGN, client);
				}
				case WAIT_PRICE_SIGN -> {
					if (!actions.screenTitleContains(client, "Edit Sign Message")) {
						timeout(client, config.screenTimeoutMs(), "price sign editor did not open");
						return;
					}
					transition(RealAuctionState.SUBMIT_PRICE_SIGN, client);
				}
				case SUBMIT_PRICE_SIGN -> {
					String priceText = PriceTextFormatter.forSign(currentItem().price());
					debug(client, "Typing recommended price: " + priceText + " (" + currentItem().price() + " coins).");
					actions.submitSignText(client, priceText);
					transition(RealAuctionState.WAIT_PRICE_APPLIED, client);
				}
				case WAIT_PRICE_APPLIED -> {
					if (!actions.screenTitleContains(client, "Create BIN Auction")) {
						timeout(client, config.screenTimeoutMs(), "Create BIN Auction screen did not return after price entry");
						return;
					}
					debug(client, "Price applied; creating BIN auction.");
					transition(RealAuctionState.CREATE_BIN, client);
				}
				case CREATE_BIN -> {
					actions.clickSlot(client, CREATE_BIN_BUTTON_SLOT);
					transition(RealAuctionState.WAIT_CONFIRM, client);
				}
				case WAIT_CONFIRM -> {
					if (!actions.screenTitleContains(client, "Confirm BIN Auction")) {
						timeout(client, config.screenTimeoutMs(), "Confirm BIN Auction screen did not open");
						return;
					}
					transition(RealAuctionState.CONFIRM, client);
				}
				case CONFIRM -> {
					debug(client, "Confirming BIN auction for " + currentItem().inventoryItemName() + " at "
						+ PriceTextFormatter.forSign(currentItem().price()) + ".");
					actions.clickSlot(client, CONFIRM_BIN_SLOT);
					transition(RealAuctionState.NEXT_ITEM, client);
				}
				case NEXT_ITEM -> {
					debug(client, "Done listing item: " + currentItem().inventoryItemName() + " at "
						+ PriceTextFormatter.forSign(currentItem().price()) + ".");
					index++;
					if (index >= pricedArmor.size()) {
						transition(RealAuctionState.RETURN_TO_HUB, client);
					} else {
						transition(RealAuctionState.OPEN_AH, client);
					}
				}
				case RETURN_TO_HUB -> {
					debug(client, "All items processed. Sending " + config.returnToHubCommand() + ".");
					actions.sendChatCommand(client, config.returnToHubCommand());
					transition(RealAuctionState.WAIT_DISCONNECT, client, config.disconnectDelayMs());
				}
				case WAIT_DISCONNECT -> {
					debug(client, "Disconnecting after listing workflow.");
					actions.disconnect(client, "AutoAuction finished listing.");
					transition(RealAuctionState.DONE, client);
					realWorkflow = null;
					notifyInfoAsync("real auction workflow finished and disconnected.");
				}
				case DONE, ERROR -> {
				}
			}
		}

		private PricedArmor currentItem() {
			if (index < 0 || index >= pricedArmor.size()) {
				throw new IllegalStateException("no current priced armor item");
			}
			return pricedArmor.get(index);
		}

		private void transition(RealAuctionState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(RealAuctionState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(delayMs, effectiveClickDelayMs());
			Autoauction.LOGGER.info("AutoAuction real workflow state: {}", next);
		}

		private void delay() {
			nextActionAt = System.currentTimeMillis() + effectiveClickDelayMs();
		}

		private int effectiveClickDelayMs() {
			return Math.max(config.clickDelayMs(), 650);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void fail(Minecraft client, String message) {
			state = RealAuctionState.ERROR;
			realWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction real workflow failed: {}", message);
			notifyIssueAsync("real auction workflow failed: " + message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction failed: " + message));
			}
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}
	}

	private void notifyInfoAsync(String message) {
		CompletableFuture.runAsync(() -> {
			try {
				notifier.info(message);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction Discord notification failed", e);
			}
		});
	}
}
