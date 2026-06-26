package com.autoauction.client;

import com.autoauction.Autoauction;
import com.autoauction.client.api.AuctionApiClient;
import com.autoauction.client.automation.AuctionAutomationController;
import com.autoauction.client.automation.AutomationState;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.config.AutoAuctionConfigStore;
import com.autoauction.client.control.ModSocketClient;
import com.autoauction.client.debug.SkyBlockIslandGuard;
import com.autoauction.client.debug.SkyBlockStatus;
import com.autoauction.client.debug.StatusDumpFormatter;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.domain.ArmorStatusFormatter;
import com.autoauction.client.domain.AuctionBlockedMessageDetector;
import com.autoauction.client.domain.AuctionItemRequestFactory;
import com.autoauction.client.domain.ModAccountStatusDetector;
import com.autoauction.client.domain.PriceTextFormatter;
import com.autoauction.client.domain.TestArmorSnapshots;
import com.autoauction.client.handoff.AltManagerHandoffClient;
import com.autoauction.client.macro.NebulaLatestLogWatcher;
import com.autoauction.client.macro.NebulaMacroController;
import com.autoauction.client.macro.NebulaMacroToggleKey;
import com.autoauction.client.minecraft.MinecraftGameActions;
import com.autoauction.client.notify.DiscordNotifier;
import com.autoauction.client.transfer.BazaarTransferWorkflow;
import com.autoauction.client.transfer.BazaarProductId;
import com.autoauction.client.transfer.BazaarTransferEstimate;
import com.autoauction.client.transfer.CoinAmountParser;
import com.autoauction.client.transfer.EnderChestParkingPlan;
import com.autoauction.client.transfer.HypixelBazaarClient;
import com.autoauction.client.transfer.TransferAccountListRequests;
import com.autoauction.client.transfer.TransferChatComponents;
import com.autoauction.client.transfer.TransferCommandSuggestions;
import com.autoauction.client.transfer.TransferController;
import com.autoauction.client.transfer.TransferDebugMessages;
import com.autoauction.client.transfer.TransferLoopGoal;
import com.autoauction.client.transfer.TransferPurseTracker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class AutoauctionClient implements ClientModInitializer {
	private static final int TEST_LISTING_PRICE = 2_500_000;
	private static final int REAL_ARMOR_LISTING_PRICE = 24_999_000;
	private static final int MIN_CLICK_DELAY_MS = 400;
	private static final long PROXY_READY_RECONNECT_DELAY_MS = 1_500L;
	private static final long HYPIXEL_READY_MACRO_DELAY_MS = 500L;
	private static final int ISLAND_COMMAND_COOLDOWN_DELAY_MS = 5_500;
	private static final int BAZAAR_CLOSE_DELAY_MS = 1_000;
	private static final int INSTANT_SELL_WARNING_DELAY_MS = 6_000;
	private static final int INSTANT_SELL_WARNING_GRACE_MS = 1_500;
	private static final int INSTANT_BUY_CONFIRM_RETRY_DELAY_MS = 1_500;
	private static final int INSTANT_BUY_CONFIRM_MAX_CLICKS = 3;
	private static final int EC_STORAGE_FIRST_SLOT = 9;
	private static final int EC_STORAGE_LAST_SLOT = 53;
	private static final int PLAYER_INVENTORY_FIRST_SLOT = 54;
	private static final int PLAYER_INVENTORY_LAST_SLOT = 89;
	private static final int REMOTE_SCREENSHOT_MAX_WIDTH = 1280;
	private static final int REMOTE_SCREENSHOT_MAX_HEIGHT = 720;

	private AutoAuctionConfig config;
	private AutoAuctionConfigStore configStore;
	private AuctionAutomationController controller;
	private MinecraftGameActions actions;
	private AuctionApiClient apiClient;
	private ModSocketClient modSocketClient;
	private TransferController transferController;
	private final TransferPurseTracker transferPurseTracker = new TransferPurseTracker();
	private final TransferAccountListRequests transferAccountListRequests = new TransferAccountListRequests();
	private DiscordNotifier notifier;
	private AltManagerHandoffClient handoffClient;
	private NebulaMacroController macroController;
	private NebulaLatestLogWatcher nebulaLatestLogWatcher;
	private final AuctionItemRequestFactory requestFactory = new AuctionItemRequestFactory();
	private int tickCounter;
	private int nebulaLatestLogPollTicks;
	private boolean sendingAutoAuctionMacroToggleCommand;
	private boolean nebulaMacroKeybindWasDown;
	private Path cachedNebulaMacroConfigPath;
	private long cachedNebulaMacroConfigModifiedAt = Long.MIN_VALUE;
	private OptionalInt cachedNebulaMacroConfigKeyCode = OptionalInt.empty();
	private boolean workflowStarted;
	private boolean dumpSlotsKeyWasDown;
	private boolean emergencyStopKeyWasDown;
	private long lastCookieBuffAlertAt;
	private RealAuctionWorkflow realWorkflow;
	private ReceiverBuyOrderWorkflow receiverBuyOrderWorkflow;
	private ReceiverSellOfferWorkflow receiverSellOfferWorkflow;
	private SenderPrepareTransferWorkflow senderPrepareTransferWorkflow;
	private SenderInstantSellWorkflow senderInstantSellWorkflow;
	private SenderInstantBuyWorkflow senderInstantBuyWorkflow;
	private SenderEnderChestRestoreWorkflow senderEnderChestRestoreWorkflow;
	private ReceiverClaimSellOfferWorkflow receiverClaimSellOfferWorkflow;
	private PendingTransferFill pendingTransferFill;
	private PendingTransferSellFill pendingTransferSellFill;
	private TransferLoopGoal transferLoopGoal;
	private NebulaMacroController.ObservedState lastNebulaDebugObservedState = NebulaMacroController.ObservedState.UNKNOWN;
	private boolean lastNebulaDebugDesiredOn;
	private int autoRetoggleCount;
	private String senderParkedItemName;
	private int senderParkedStacks;
	private PendingHandoff pendingHandoff;

	@Override
	public void onInitializeClient() {
		try {
			this.configStore = new AutoAuctionConfigStore(FabricLoader.getInstance().getConfigDir());
			this.config = configStore.load();
			this.controller = new AuctionAutomationController(config);
			this.actions = new MinecraftGameActions();
			this.apiClient = new AuctionApiClient(config);
			this.transferController = new TransferController();
			this.modSocketClient = new ModSocketClient(
				config,
				this::disconnectFromRemoteCommand,
				transferSocketHandler(),
				this::handleRemoteScreenshotRequest,
				this::handleRemoteActionRequest
			);
			this.notifier = new DiscordNotifier(config);
			this.handoffClient = new AltManagerHandoffClient();
			this.macroController = new NebulaMacroController();
			this.nebulaLatestLogWatcher = new NebulaLatestLogWatcher(
				FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log")
			);
			registerCommands();
			registerOutgoingCommandHandlers();
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
				pollNebulaLatestLog();
				handlePendingHandoff(client);
				if (client.player == null || controller == null || actions == null) {
					return;
				}

				observeNebulaMacroKeybind(client);
				autoRestoreNebulaMacroIfNeeded(client);
				handleDumpSlotsHotkey(client);
				handleEmergencyStopHotkey(client);
				if (realWorkflow != null) {
					realWorkflow.tick(client);
				}
				if (receiverBuyOrderWorkflow != null) {
					receiverBuyOrderWorkflow.tick(client);
				}
				if (receiverSellOfferWorkflow != null) {
					receiverSellOfferWorkflow.tick(client);
				}
				if (senderPrepareTransferWorkflow != null) {
					senderPrepareTransferWorkflow.tick(client);
				}
				if (senderInstantSellWorkflow != null) {
					senderInstantSellWorkflow.tick(client);
				}
				if (senderEnderChestRestoreWorkflow != null) {
					senderEnderChestRestoreWorkflow.tick(client);
				} else if (senderInstantBuyWorkflow != null) {
					senderInstantBuyWorkflow.tick(client);
				}
				if (receiverClaimSellOfferWorkflow != null) {
					receiverClaimSellOfferWorkflow.tick(client);
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
		if (config.apiToken().isBlank() || client.getUser() == null) {
			return;
		}
		String clientVersion = FabricLoader.getInstance().getModContainer("autoauction")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
		modSocketClient.ensureStartedFor(client.getUser().getName(), clientVersion);
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
			notifyBanAsync(Minecraft.getInstance().getUser().getName(), details.reason());
			Autoauction.LOGGER.info(
				"AutoAuction reported account status banned from {}. reason={} banId={} banUntil={}",
				source,
				details.reason(),
				details.banId(),
				details.banUntil()
			);
		}
	}

	private void disconnectFromRemoteCommand(String reason) {
		Minecraft client = Minecraft.getInstance();
		String disconnectReason = String.valueOf(reason == null ? "" : reason).isBlank()
			? "AutoAuction remote disconnect requested."
			: reason;
		client.execute(() -> {
			if (actions != null) {
				actions.disconnect(client, disconnectReason);
			}
		});
	}

	private void handleRemoteActionRequest(ModSocketClient.RemoteAction request) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (modSocketClient == null || actions == null || client.player == null) {
				sendRemoteClientLog("warn", "Remote action skipped: Minecraft player is not ready");
				return;
			}
			String actionType = String.valueOf(request.actionType() == null ? "" : request.actionType()).trim().toLowerCase(Locale.ROOT);
			String content = String.valueOf(request.content() == null ? "" : request.content()).trim();
			if (content.isBlank()) {
				sendRemoteClientLog("warn", "Remote action skipped: empty message");
				return;
			}

			try {
				switch (actionType) {
					case "client_command" -> sendRemoteClientCommand(client, content);
					case "server_command" -> sendRemoteServerCommand(client, content);
					case "text_message" -> {
						actions.sendChatMessage(client, content);
						sendRemoteClientLog("info", "Remote chat message sent");
					}
					default -> sendRemoteClientLog("warn", "Remote action skipped: unknown action type " + actionType);
				}
			} catch (RuntimeException error) {
				Autoauction.LOGGER.warn("AutoAuction remote action failed", error);
				sendRemoteClientLog("error", "Remote action failed: " + error.getMessage());
			}
		});
	}

	private void sendRemoteClientCommand(Minecraft client, String content) {
		String command = normalizedRemoteCommand(content);
		if (command.isBlank()) {
			sendRemoteClientLog("warn", "Remote client command skipped: empty command");
			return;
		}
		if (NebulaMacroController.isToggleCommand(command)) {
			recordManualMacroToggleIntent("remote client command");
		}
		boolean handled = actions.sendClientCommand(client, command);
		if (handled) {
			sendRemoteClientLog("info", "Remote client command sent: /" + command);
		} else {
			sendRemoteClientLog("warn", "Remote client command was not handled: /" + command);
		}
	}

	private void sendRemoteServerCommand(Minecraft client, String content) {
		String command = normalizedRemoteCommand(content);
		if (command.isBlank()) {
			sendRemoteClientLog("warn", "Remote server command skipped: empty command");
			return;
		}
		actions.sendChatCommand(client, command);
		sendRemoteClientLog("info", "Remote server command sent: /" + command);
	}

	private String normalizedRemoteCommand(String content) {
		String command = String.valueOf(content == null ? "" : content).trim();
		while (command.startsWith("/") || command.startsWith(".")) {
			command = command.substring(1).trim();
		}
		return command;
	}

	private void handleRemoteScreenshotRequest(ModSocketClient.ScreenshotRequest request) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (modSocketClient == null) {
				return;
			}
			try {
				Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> sendRemoteScreenshot(image, request));
			} catch (RuntimeException error) {
				Autoauction.LOGGER.warn("AutoAuction remote screenshot failed", error);
				modSocketClient.sendClientLog("error", "Remote screenshot failed: " + error.getMessage());
			}
		});
	}

	private void sendRemoteScreenshot(NativeImage image, ModSocketClient.ScreenshotRequest request) {
		Path tempFile = null;
		try (image) {
			NativeImage outputImage = resizeRemoteScreenshot(image);
			tempFile = Files.createTempFile("autoauction-remote-", ".png");
			try {
				outputImage.writeToFile(tempFile);
				byte[] imageBytes = Files.readAllBytes(tempFile);
				String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
				if (modSocketClient != null && modSocketClient.sendClientScreenshot("image/png", imageBase64, Instant.now().toString())) {
					modSocketClient.sendClientLog("debug", String.format(
						"Remote screenshot captured for request %s (%dx%d, %d KB)",
						request.requestId(),
						outputImage.getWidth(),
						outputImage.getHeight(),
						Math.max(1, imageBytes.length / 1024)
					));
				}
			} finally {
				if (outputImage != image) {
					outputImage.close();
				}
			}
		} catch (Exception error) {
			Autoauction.LOGGER.warn("AutoAuction remote screenshot encode failed", error);
			if (modSocketClient != null) {
				modSocketClient.sendClientLog("error", "Remote screenshot encode failed: " + error.getMessage());
			}
		} finally {
			if (tempFile != null) {
				try {
					Files.deleteIfExists(tempFile);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private NativeImage resizeRemoteScreenshot(NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		double scale = Math.min(
			1.0D,
			Math.min(
				(double) REMOTE_SCREENSHOT_MAX_WIDTH / Math.max(1, width),
				(double) REMOTE_SCREENSHOT_MAX_HEIGHT / Math.max(1, height)
			)
		);
		if (scale >= 1.0D) {
			return image;
		}
		int resizedWidth = Math.max(1, (int) Math.round(width * scale));
		int resizedHeight = Math.max(1, (int) Math.round(height * scale));
		NativeImage resized = new NativeImage(resizedWidth, resizedHeight, false);
		image.resizeSubRectTo(0, 0, width, height, resized);
		return resized;
	}

	private void handlePendingHandoff(Minecraft client) {
		if (pendingHandoff == null || controller == null || actions == null) {
			return;
		}

		long elapsed = System.currentTimeMillis() - pendingHandoff.startedAt;
		String currentUsername = client.getUser() == null ? "" : client.getUser().getName();
		boolean switched = !currentUsername.isBlank()
			&& !currentUsername.equalsIgnoreCase(pendingHandoff.previousUsername)
			&& (pendingHandoff.targetUsername.isBlank() || currentUsername.equalsIgnoreCase(pendingHandoff.targetUsername));

		if (!switched) {
			if (elapsed > 60_000) {
				notifyIssueAsync("account handoff timed out waiting for " + pendingHandoff.targetUsername);
				Autoauction.LOGGER.warn("AutoAuction account handoff timed out waiting for {}", pendingHandoff.targetUsername);
				pendingHandoff = null;
				workflowStarted = false;
				controller.start();
			}
			return;
		}

		if (!pendingHandoff.reconnectStarted) {
			if (handoffClient.proxyReadiness(pendingHandoff.targetUsername, pendingHandoff.targetUuid)
				!= AltManagerHandoffClient.ProxyReadiness.READY) {
				if (elapsed > 60_000) {
					notifyIssueAsync("account handoff timed out waiting for proxy for " + pendingHandoff.targetUsername);
					Autoauction.LOGGER.warn("AutoAuction account handoff timed out waiting for proxy for {}", pendingHandoff.targetUsername);
					pendingHandoff = null;
					workflowStarted = false;
					controller.start();
				}
				return;
			}
			if (pendingHandoff.proxyReadyAt == 0L) {
				pendingHandoff.proxyReadyAt = System.currentTimeMillis();
				Autoauction.LOGGER.info("AutoAuction handoff proxy ready for {}; waiting before reconnect.", currentUsername);
				return;
			}
			if (client.player != null || !proxyReadyDelayElapsed(pendingHandoff.proxyReadyAt, System.currentTimeMillis())) {
				return;
			}
			pendingHandoff.reconnectStarted = true;
			pendingHandoff.reconnectStartedAt = System.currentTimeMillis();
			Autoauction.LOGGER.info("AutoAuction handoff switched to {}; reconnecting to Hypixel.", currentUsername);
			actions.connectToServer(client, "mc.hypixel.net");
			return;
		}

		if (client.player == null || client.getCurrentServer() == null
			|| !ModAccountStatusDetector.isHypixelServer(client.getCurrentServer().ip)) {
			pendingHandoff.hypixelReadyAt = 0L;
			return;
		}

		if (pendingHandoff.hypixelReadyAt == 0L) {
			pendingHandoff.hypixelReadyAt = System.currentTimeMillis();
			Autoauction.LOGGER.info("AutoAuction handoff loaded into Hypixel as {}; waiting before macro start.", currentUsername);
			return;
		}
		if (!hypixelReadyDelayElapsed(pendingHandoff.hypixelReadyAt, System.currentTimeMillis())) {
			return;
		}

		NebulaMacroController.EnsureResult macroStart = macroController.ensureOn(
			command -> runMacroToggleCommand(client, command),
			System.currentTimeMillis()
		);
		if (macroStart == NebulaMacroController.EnsureResult.PENDING) {
			return;
		}
		if (macroStart == NebulaMacroController.EnsureResult.FAILED) {
			notifyIssueAsync("macro enable confirmation timed out during account handoff");
			Autoauction.LOGGER.warn("AutoAuction handoff failed waiting for Nebula combat macro to enable.");
			pendingHandoff = null;
			return;
		}
		controller.start();
		workflowStarted = false;
		notifyInfoAsync("account handoff complete for " + currentUsername + ".");
		sendRemoteClientLog("info", "Handoff complete, new account is " + currentUsername);
		pendingHandoff = null;
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
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
			handleMacroChatMessage(message.getString());
			sendRemoteChatLog(message);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			handleMacroChatMessage(text);
			if (!overlay) {
				sendRemoteChatLog(message);
			}
			if (AuctionBlockedMessageDetector.isCookieBuffRequired(text)) {
				handleCookieBuffRequired();
			}
			handleTransferBuyOrderFilled(text);
			handleTransferSellOfferFilled(text);
			handleTransferInstantBuyComplete(text);
		});
	}

	private void registerOutgoingCommandHandlers() {
		ClientSendMessageEvents.COMMAND.register(command -> {
			if (sendingAutoAuctionMacroToggleCommand || !NebulaMacroController.isToggleCommand(command)) {
				return;
			}
			recordManualMacroToggleIntent("typed command");
		});
	}

	private void handleTransferBuyOrderFilled(String message) {
		PendingTransferFill pending = pendingTransferFill;
		if (pending == null || !BazaarTransferWorkflow.isBuyOrderFilledMessage(message, pending.itemName())) {
			return;
		}
		pendingTransferFill = null;
		Minecraft.getInstance().execute(() -> {
			if (receiverSellOfferWorkflow != null) {
				return;
			}
			receiverSellOfferWorkflow = new ReceiverSellOfferWorkflow(pending.itemName(), pending.quantity());
			receiverSellOfferWorkflow.start(Minecraft.getInstance());
		});
	}

	private void handleTransferSellOfferFilled(String message) {
		PendingTransferSellFill pending = pendingTransferSellFill;
		if (pending == null || !BazaarTransferWorkflow.isSellOfferFilledMessage(message, pending.itemName())) {
			return;
		}
		pendingTransferSellFill = null;
		startReceiverClaimSellOfferWorkflow(pending.itemName(), pending.quantity());
	}

	private void handleTransferInstantBuyComplete(String message) {
		SenderInstantBuyWorkflow workflow = senderInstantBuyWorkflow;
		if (workflow == null) {
			return;
		}
		workflow.onChatMessage(message);
	}

	private void startReceiverClaimSellOfferWorkflow(String itemName, int quantity) {
		Minecraft.getInstance().execute(() -> {
			if (receiverClaimSellOfferWorkflow != null) {
				return;
			}
			receiverClaimSellOfferWorkflow = new ReceiverClaimSellOfferWorkflow(itemName, quantity);
			receiverClaimSellOfferWorkflow.start(Minecraft.getInstance());
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

	private ModSocketClient.TransferHandler transferSocketHandler() {
		return new ModSocketClient.TransferHandler() {
			@Override
			public void onAccounts(List<ModSocketClient.TransferAccount> accounts) {
				List<TransferController.ConnectedAccount> connectedAccounts = accounts.stream()
					.map(account -> new TransferController.ConnectedAccount(account.minecraftUsername(), account.status()))
					.toList();
				if (transferAccountListRequests.consumeSilentResponse()) {
					transferController.showAccounts(connectedAccounts);
					return;
				}
				sendTransferFeedback(transferController.showAccounts(connectedAccounts));
			}

			@Override
			public void onInvite(ModSocketClient.TransferSession session) {
				sendRemoteClientLog("info", "Transfer invite received from " + session.senderUsername() + " for " + session.itemName());
				sendTransferFeedback(transferController.incomingInvite(toTransferSession(session)));
			}

			@Override
			public void onPending(ModSocketClient.TransferSession session) {
				sendRemoteClientLog("info", "Transfer invite pending for " + session.receiverUsername() + " using " + session.itemName());
				sendTransferFeedback(transferController.outgoingPending(toTransferSession(session)));
			}

			@Override
			public void onAccepted(ModSocketClient.TransferSession session, String role) {
				transferLoopGoal = null;
				sendRemoteClientLog("info", "Transfer accepted as " + role + " for " + session.itemName());
				sendTransferFeedback(transferController.accepted(toTransferSession(session), transferRole(role)));
			}

			@Override
			public void onDeclined(ModSocketClient.TransferSession session, String reason) {
				sendRemoteClientLog("warn", "Transfer declined: " + reason);
				sendTransferFeedback(transferController.declined(reason));
			}

			@Override
			public void onCancelled(String sessionId, String reason) {
				sendRemoteClientLog("warn", "Transfer cancelled: " + reason);
				sendTransferFeedback(transferController.cancelled(reason));
				receiverBuyOrderWorkflow = null;
				receiverSellOfferWorkflow = null;
				senderPrepareTransferWorkflow = null;
				senderInstantSellWorkflow = null;
				senderInstantBuyWorkflow = null;
				senderEnderChestRestoreWorkflow = null;
				receiverClaimSellOfferWorkflow = null;
				pendingTransferFill = null;
				pendingTransferSellFill = null;
				transferLoopGoal = null;
				transferPurseTracker.clear();
				Minecraft.getInstance().execute(() -> startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance()));
			}

			@Override
			public void onRun(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendRemoteClientLog("info", "Transfer run started: receiver creating buy order for " + run.quantity() + "x " + session.itemName());
				sendTransferFeedback(transferController.incomingRun(run.quantity()));
				Minecraft.getInstance().execute(() -> {
					startTransferPurseTracking("receiver", Minecraft.getInstance());
					receiverBuyOrderWorkflow = new ReceiverBuyOrderWorkflow(session.itemName(), run.quantity());
					receiverBuyOrderWorkflow.start(Minecraft.getInstance());
				});
			}

			@Override
			public void onRunSent(ModSocketClient.TransferRun run) {
				sendTransferFeedback(transferController.runSent(run.quantity()));
			}

			@Override
			public void onBuyOrderReady(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendRemoteClientLog("info", "Buy order ready: sender instant-selling " + run.quantity() + "x " + session.itemName());
				sendTransferFeedback("AutoAuction transfer buy order ready: selling " + run.quantity() + "x " + session.itemName() + ".");
				Minecraft.getInstance().execute(() -> {
					senderInstantSellWorkflow = new SenderInstantSellWorkflow(session.itemName(), run.quantity());
					senderInstantSellWorkflow.start(Minecraft.getInstance());
				});
			}

			@Override
			public void onSellOfferReady(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendRemoteClientLog("info", "Sell offer ready: sender instant-buying " + run.quantity() + "x " + session.itemName());
				sendTransferFeedback("AutoAuction transfer sell offer ready: buying " + run.quantity() + "x " + session.itemName() + ".");
				Minecraft.getInstance().execute(() -> {
					senderInstantBuyWorkflow = new SenderInstantBuyWorkflow(session.itemName(), run.quantity());
					senderInstantBuyWorkflow.start(Minecraft.getInstance());
				});
			}

			@Override
			public void onSellOfferBought(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendRemoteClientLog("info", "Sell offer bought: receiver claiming coins for " + run.quantity() + "x " + session.itemName());
				sendTransferFeedback("AutoAuction transfer sell offer bought: claiming coins for " + run.quantity() + "x " + session.itemName() + ".");
				pendingTransferSellFill = null;
				startReceiverClaimSellOfferWorkflow(session.itemName(), run.quantity());
			}

			@Override
			public void onCycleComplete(ModSocketClient.TransferCycle cycle) {
				handleTransferCycleComplete(cycle);
			}

			@Override
			public void onError(String code, String message) {
				sendRemoteClientLog("error", "Transfer error " + code + ": " + message);
				sendTransferFeedback(transferController.error(code, message));
				receiverBuyOrderWorkflow = null;
				receiverSellOfferWorkflow = null;
				senderPrepareTransferWorkflow = null;
				senderInstantSellWorkflow = null;
				senderInstantBuyWorkflow = null;
				senderEnderChestRestoreWorkflow = null;
				receiverClaimSellOfferWorkflow = null;
				pendingTransferFill = null;
				pendingTransferSellFill = null;
				transferLoopGoal = null;
				transferPurseTracker.clear();
				Minecraft.getInstance().execute(() -> startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance()));
			}
		};
	}

	private TransferController.Session toTransferSession(ModSocketClient.TransferSession session) {
		return new TransferController.Session(
			session.id(),
			session.senderUsername(),
			session.receiverUsername(),
			session.itemName()
		);
	}

	private void handleTransferCycleComplete(ModSocketClient.TransferCycle cycle) {
		TransferLoopGoal goal = transferLoopGoal;
		String itemName = cycle.session().itemName();
		sendRemoteClientLog("info", "Transfer cycle complete for " + cycle.quantity() + "x " + itemName
			+ ": before=" + cycle.before() + " after=" + cycle.after() + " delta=" + cycle.delta());
		sendTransferFeedback("AutoAuction transfer cycle complete for " + cycle.quantity() + "x " + itemName
			+ ": receiver delta=" + formatSignedCoins(cycle.delta()) + ".");
		if (goal == null) {
			Minecraft.getInstance().execute(() -> startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance()));
			return;
		}

		TransferLoopGoal.Progress progress = goal.recordCycle(cycle.delta(), cycle.before(), cycle.after());
		sendTransferFeedback("AutoAuction transfer target progress: cycle " + progress.cycles()
			+ ", total=" + formatCoins(progress.totalTransferred())
			+ "/" + formatCoins(progress.targetCoins())
			+ ", remaining=" + formatCoins(progress.remaining())
			+ ", estimated cycles left=" + progress.estimatedCyclesRemaining() + ".");
		if (progress.complete()) {
			transferLoopGoal = null;
			sendTransferFeedback("AutoAuction transfer target reached after " + progress.cycles()
				+ " cycles. Receiver purse start=" + formatCoins(progress.startingPurse())
				+ ", done=" + formatCoins(progress.donePurse())
				+ ", total delta=" + formatSignedCoins(progress.totalPurseDelta()) + ".");
			Minecraft.getInstance().execute(() -> startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance()));
			return;
		}
		if (cycle.delta() <= 0) {
			transferLoopGoal = null;
			sendTransferFeedback("AutoAuction transfer loop stopped: receiver delta was not positive, so continuing would not move toward the target.");
			Minecraft.getInstance().execute(() -> startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance()));
			return;
		}

		Minecraft.getInstance().execute(() -> {
			if (!transferController.canRunAsSender()) {
				transferLoopGoal = null;
				sendTransferFeedback("AutoAuction transfer loop stopped: this account is no longer the paired sender.");
				startSenderEnderChestRestoreIfNeeded(Minecraft.getInstance());
				return;
			}
			requestPreparedTransferRun(Minecraft.getInstance(), itemName, true);
		});
	}

	private TransferController.Role transferRole(String role) {
		return "receiver".equalsIgnoreCase(role) ? TransferController.Role.RECEIVER : TransferController.Role.SENDER;
	}

	private void sendTransferFeedback(String message) {
		sendTransferFeedback(List.of(message));
	}

	private void sendTransferFeedback(List<String> messages) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player == null) {
				for (String message : messages) {
					Autoauction.LOGGER.info(message);
				}
				return;
			}
			for (String message : messages) {
				client.player.sendSystemMessage(TransferChatComponents.forMessage(message));
			}
		});
	}

	private void handleMacroChatMessage(String text) {
		if (macroController != null) {
			NebulaMacroController.ObservedState beforeState = macroController.observedState();
			boolean beforeDesiredOn = macroController.desiredOn();
			macroController.onChatMessage(text);
			reportNebulaMacroIntentIfChanged(beforeState, beforeDesiredOn);
		}
	}

	private void reportNebulaMacroIntentIfChanged(NebulaMacroController.ObservedState beforeState, boolean beforeDesiredOn) {
		NebulaMacroController.ObservedState observedState = macroController.observedState();
		boolean desiredOn = macroController.desiredOn();
		if (observedState == beforeState && desiredOn == beforeDesiredOn
			&& observedState == lastNebulaDebugObservedState && desiredOn == lastNebulaDebugDesiredOn) {
			return;
		}
		lastNebulaDebugObservedState = observedState;
		lastNebulaDebugDesiredOn = desiredOn;
		if (observedState == NebulaMacroController.ObservedState.OFF && !desiredOn) {
			sendRemoteDebugLog("info", "nebula",
				"Nebula combat macro disabled observed; auto-retoggle idle because desired state is OFF/manual disable intent.");
		}
	}

	private void observeNebulaMacroKeybind(Minecraft client) {
		if (config == null) {
			return;
		}
		ResolvedMacroKey resolvedKey = resolveNebulaMacroToggleKey(client);
		if (resolvedKey.keyCode().isEmpty()) {
			nebulaMacroKeybindWasDown = false;
			return;
		}
		boolean isDown = GLFW.glfwGetKey(client.getWindow().handle(), resolvedKey.keyCode().getAsInt()) == GLFW.GLFW_PRESS;
		if (isDown && !nebulaMacroKeybindWasDown) {
			recordManualMacroToggleIntent(resolvedKey.source());
		}
		nebulaMacroKeybindWasDown = isDown;
	}

	private ResolvedMacroKey resolveNebulaMacroToggleKey(Minecraft client) {
		OptionalInt nebulaConfigKey = resolveCachedNebulaMacroConfigKey(client);
		if (nebulaConfigKey.isPresent()) {
			return new ResolvedMacroKey(nebulaConfigKey, "Nebula config key code " + nebulaConfigKey.getAsInt());
		}
		OptionalInt configuredKey = NebulaMacroToggleKey.resolve(config.nebulaMacroToggleKey());
		return new ResolvedMacroKey(configuredKey, "configured Nebula key " + config.nebulaMacroToggleKey());
	}

	private OptionalInt resolveCachedNebulaMacroConfigKey(Minecraft client) {
		Path configPath = NebulaMacroToggleKey.configPath(client.gameDirectory.toPath());
		long modifiedAt = Long.MIN_VALUE;
		try {
			if (Files.exists(configPath)) {
				modifiedAt = Files.getLastModifiedTime(configPath).toMillis();
			}
			if (configPath.equals(cachedNebulaMacroConfigPath) && modifiedAt == cachedNebulaMacroConfigModifiedAt) {
				return cachedNebulaMacroConfigKeyCode;
			}
			cachedNebulaMacroConfigPath = configPath;
			cachedNebulaMacroConfigModifiedAt = modifiedAt;
			cachedNebulaMacroConfigKeyCode = NebulaMacroToggleKey.resolveFromGameDirectory(client.gameDirectory.toPath());
			if (cachedNebulaMacroConfigKeyCode.isPresent()) {
				sendRemoteDebugLog("info", "nebula",
					"Loaded Nebula combat macro key code " + cachedNebulaMacroConfigKeyCode.getAsInt() + " from " + configPath);
			}
		} catch (Exception error) {
			cachedNebulaMacroConfigPath = configPath;
			cachedNebulaMacroConfigModifiedAt = modifiedAt;
			cachedNebulaMacroConfigKeyCode = OptionalInt.empty();
			sendRemoteDebugLog("warn", "nebula",
				"Could not load Nebula combat macro key from " + configPath + "; falling back to autoauction.json.");
			Autoauction.LOGGER.debug("AutoAuction could not load Nebula macro key config", error);
		}
		return cachedNebulaMacroConfigKeyCode;
	}

	private void recordManualMacroToggleIntent(String source) {
		if (macroController == null) {
			return;
		}
		NebulaMacroController.ManualToggleIntentResult result = macroController.recordManualToggleIntent(System.currentTimeMillis());
		String message = switch (result) {
			case ENABLING -> "Manual Nebula combat macro enable detected from " + source + "; auto-retoggle remains active.";
			case DISABLING -> "Manual Nebula combat macro disable detected from " + source + "; auto-retoggle paused.";
			case UNKNOWN -> "Manual Nebula combat macro toggle detected from " + source + "; waiting for Nebula status.";
		};
		sendRemoteDebugLog("info", "nebula", message);
	}

	private void autoRestoreNebulaMacroIfNeeded(Minecraft client) {
		if (macroController == null) {
			return;
		}
		NebulaMacroController.AutoRestoreResult result = macroController.autoRestoreIfDisabled(
			command -> runMacroToggleCommand(client, command),
			System.currentTimeMillis()
		);
		switch (result) {
			case STARTED -> {
				autoRetoggleCount++;
				sendRemoteDebugLog("warn", "nebula", "Nebula combat macro disabled while desired on; retoggling.");
			}
			case FAILED -> sendRemoteDebugLog("error", "nebula", "Nebula combat macro auto-retoggle timed out; will retry.");
			default -> {
			}
		}
	}

	private void pollNebulaLatestLog() {
		if (nebulaLatestLogWatcher == null || ++nebulaLatestLogPollTicks < 20) {
			return;
		}
		nebulaLatestLogPollTicks = 0;
		try {
			for (String message : nebulaLatestLogWatcher.pollMacroMessages()) {
				handleMacroChatMessage(message);
				sendRemoteDebugLog("info", "nebula", NebulaLatestLogWatcher.displayMessage(message));
			}
		} catch (Exception error) {
			Autoauction.LOGGER.debug("AutoAuction could not poll latest.log for Nebula macro messages", error);
		}
	}

	private void sendRemoteClientLog(String level, String message) {
		sendRemoteClientLog(level, "system", message);
	}

	private void sendRemoteClientLog(String level, String source, String message) {
		if (modSocketClient != null && !modSocketClient.sendClientLog(level, source, message)) {
			Autoauction.LOGGER.debug("AutoAuction remote client log skipped: {}", message);
		}
	}

	private void sendRemoteDebugLog(String level, String source, String message) {
		if (isDebugEnabled()) {
			Autoauction.LOGGER.info("[AutoAuction debug/{}] {}", source, message);
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("[AutoAuction debug/" + source + "] " + message));
			}
			sendRemoteClientLog(level, source, message);
		}
	}

	private void runMacroToggleCommand(Minecraft client, String command) {
		if (actions != null) {
			sendingAutoAuctionMacroToggleCommand = true;
			try {
				actions.sendClientCommand(client, command);
			} finally {
				sendingAutoAuctionMacroToggleCommand = false;
			}
		}
	}

	private void sendRemoteChatLog(String message) {
		if (modSocketClient != null && !modSocketClient.sendClientLog("info", "chat", message)) {
			Autoauction.LOGGER.debug("AutoAuction remote chat log skipped: {}", message);
		}
	}

	private void sendRemoteChatLog(Component message) {
		List<ModSocketClient.RemoteLogSegment> segments = remoteLogSegments(message);
		String plainMessage = message.getString();
		if (modSocketClient != null && !modSocketClient.sendClientLog("info", "chat", plainMessage, segments)) {
			Autoauction.LOGGER.debug("AutoAuction remote chat log skipped: {}", plainMessage);
		}
	}

	private List<ModSocketClient.RemoteLogSegment> remoteLogSegments(Component message) {
		List<ModSocketClient.RemoteLogSegment> segments = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		Style[] currentStyle = new Style[] { null };
		message.getVisualOrderText().accept((index, style, codePoint) -> {
			if (currentStyle[0] != null && !sameRemoteLogStyle(currentStyle[0], style)) {
				appendRemoteLogSegment(segments, text, currentStyle[0]);
				text.setLength(0);
			}
			currentStyle[0] = style;
			text.appendCodePoint(codePoint);
			return true;
		});
		if (currentStyle[0] != null) {
			appendRemoteLogSegment(segments, text, currentStyle[0]);
		}
		return segments;
	}

	private void appendRemoteLogSegment(List<ModSocketClient.RemoteLogSegment> segments, StringBuilder text, Style style) {
		if (text.isEmpty()) {
			return;
		}
		segments.add(new ModSocketClient.RemoteLogSegment(
			text.toString(),
			remoteLogColor(style),
			style.isBold(),
			style.isItalic(),
			style.isUnderlined(),
			style.isStrikethrough()
		));
	}

	private boolean sameRemoteLogStyle(Style left, Style right) {
		return java.util.Objects.equals(remoteLogColor(left), remoteLogColor(right))
			&& left.isBold() == right.isBold()
			&& left.isItalic() == right.isItalic()
			&& left.isUnderlined() == right.isUnderlined()
			&& left.isStrikethrough() == right.isStrikethrough();
	}

	private String remoteLogColor(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return null;
		}
		return String.format("#%06X", color.getValue() & 0xFFFFFF);
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
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(autoauctionCommand());
			dispatcher.register(autoauctionTestCommand());
			dispatcher.register(moneyTransferCommand());
		});
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> autoauctionCommand() {
		return literal(AutoauctionCommandLayout.MAIN_ROOT)
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
			.then(literal("island").executes(context -> {
				sendFeedback(context.getSource(), currentIslandMessage(context.getSource().getClient()));
				return 1;
			}))
			.then(literal("armor").executes(context -> {
				EnumMap<ArmorPiece, ArmorSnapshot> armor = actions.readEquippedFinalDestinationArmor(context.getSource().getClient());
				for (String line : ArmorStatusFormatter.format(armor, config.killThreshold())) {
					sendFeedback(context.getSource(), line);
				}
				return 1;
			}))
			.then(literal("debug")
				.executes(context -> {
					sendFeedback(context.getSource(), debugStatusMessage());
					return 1;
				})
				.then(literal("on").executes(context -> setDebugEnabled(context.getSource(), true)))
				.then(literal("off").executes(context -> setDebugEnabled(context.getSource(), false)))
				.then(literal("status").executes(context -> {
					sendFeedback(context.getSource(), debugStatusMessage());
					return 1;
				})))
			.then(literal("retoggle")
				.then(literal("status").executes(context -> {
					sendFeedback(context.getSource(), retoggleStatusMessage());
					return 1;
				})));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> autoauctionTestCommand() {
		return literal(AutoauctionCommandLayout.TEST_ROOT)
			.then(literal("testtrigger").executes(context -> {
				workflowStarted = true;
				priceArmorAsync(TestArmorSnapshots.finalDestinationSet(config.killThreshold()),
					"Test trigger ran; priced fake 25k Final Destination set without clicking.");
				sendFeedback(context.getSource(), "AutoAuction testtrigger started API/Discord pricing.");
				return 1;
			}))
			.then(literal("testlisting").executes(context -> {
				Minecraft client = context.getSource().getClient();
				if (realWorkflow != null) {
					sendFeedback(context.getSource(), "AutoAuction workflow is already running. Press F9 to cancel it.");
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
			.then(literal("dumpstatus").executes(context -> {
				DumpCounts counts = dumpScoreboardAndTabList(context.getSource().getClient());
				sendFeedback(context.getSource(), "AutoAuction dumped scoreboard=" + counts.scoreboardLines() + " tablist=" + counts.tabListLines() + " to latest.log.");
				return 1;
			}));
	}

	private int setDebugEnabled(FabricClientCommandSource source, boolean enabled) {
		config = config.withDebugEnabled(enabled);
		try {
			configStore.save(config);
		} catch (Exception error) {
			Autoauction.LOGGER.error("AutoAuction failed to save debug config", error);
			sendFeedback(source, "AutoAuction debug " + (enabled ? "on" : "off") + " failed: could not save config.");
			return 0;
		}
		sendFeedback(source, "AutoAuction debug is now " + (enabled ? "ON" : "OFF") + ".");
		return 1;
	}

	private String debugStatusMessage() {
		return "AutoAuction debug is " + (isDebugEnabled() ? "ON" : "OFF") + ".";
	}

	private String retoggleStatusMessage() {
		NebulaMacroController.ObservedState observedState = macroController == null
			? NebulaMacroController.ObservedState.UNKNOWN
			: macroController.observedState();
		boolean desiredOn = macroController != null && macroController.desiredOn();
		return retoggleStatusMessage(autoRetoggleCount, observedState, desiredOn);
	}

	private boolean isDebugEnabled() {
		return config != null && config.debugEnabled();
	}

	private void sendDebugFeedback(Minecraft client, String message) {
		if (!isDebugEnabled()) {
			return;
		}
		Autoauction.LOGGER.info(message);
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal(message));
		}
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> moneyTransferCommand() {
		return literal(AutoauctionCommandLayout.TRANSFER_ROOT)
			.then(literal("list").executes(context -> {
				if (!modSocketClient.requestTransferAccounts()) {
					sendFeedback(context.getSource(), "AutoAuction transfer list failed: mod socket is not connected yet.");
					return 0;
				}
				sendFeedback(context.getSource(), "AutoAuction transfer account list requested.");
				return 1;
			}))
			.then(literal("accept")
				.then(argument("senderUsername", StringArgumentType.word())
					.suggests((context, builder) -> suggestPendingTransferSenders(builder))
					.executes(context -> {
					String senderUsername = StringArgumentType.getString(context, "senderUsername");
					if (!transferController.canAcceptFrom(senderUsername)) {
						sendFeedback(context.getSource(), "AutoAuction transfer accept failed: no pending invite from " + senderUsername + ".");
						return 0;
					}
					if (!modSocketClient.acceptTransfer(senderUsername)) {
						sendFeedback(context.getSource(), "AutoAuction transfer accept failed: mod socket is not connected yet.");
						return 0;
					}
					sendFeedback(context.getSource(), "AutoAuction transfer accept sent for " + senderUsername + ".");
					return 1;
				})))
			.then(literal("decline")
				.then(argument("senderUsername", StringArgumentType.word())
					.suggests((context, builder) -> suggestPendingTransferSenders(builder))
					.executes(context -> {
					String senderUsername = StringArgumentType.getString(context, "senderUsername");
					if (!modSocketClient.declineTransfer(senderUsername)) {
						sendFeedback(context.getSource(), "AutoAuction transfer decline failed: mod socket is not connected yet.");
						return 0;
					}
					sendFeedback(context.getSource(), "AutoAuction transfer decline sent for " + senderUsername + ".");
					return 1;
				})))
			.then(literal("run")
				.executes(context -> runTransferCommand(context.getSource(), OptionalLong.empty()))
				.then(argument("targetCoins", StringArgumentType.word()).executes(context -> {
					OptionalLong targetCoins = CoinAmountParser.parse(StringArgumentType.getString(context, "targetCoins"));
					if (targetCoins.isEmpty()) {
						sendFeedback(context.getSource(), "AutoAuction transfer run failed: target must be a positive coin amount like 200m, 2.5m, or 200000000.");
						return 0;
					}
					return runTransferCommand(context.getSource(), targetCoins);
				})))
			.then(literal("switch").executes(context -> {
				if (!modSocketClient.switchTransfer()) {
					sendFeedback(context.getSource(), "AutoAuction transfer switch failed: mod socket is not connected yet.");
					return 0;
				}
				sendFeedback(context.getSource(), "AutoAuction transfer switch requested.");
				return 1;
			}))
			.then(literal("cancel").executes(context -> {
				if (!modSocketClient.cancelTransfer()) {
					sendFeedback(context.getSource(), "AutoAuction transfer cancel failed: mod socket is not connected yet.");
					return 0;
				}
				sendFeedback(context.getSource(), "AutoAuction transfer cancel sent.");
				return 1;
			}))
			.then(literal("pair")
				.then(argument("receiverUsername", StringArgumentType.word())
					.suggests((context, builder) -> suggestTransferReceivers(builder))
					.then(argument("itemName", StringArgumentType.greedyString())
						.suggests((context, builder) -> suggestTransferItems(builder))
						.executes(context -> {
						String receiverUsername = StringArgumentType.getString(context, "receiverUsername");
						String itemName = StringArgumentType.getString(context, "itemName").trim();
						if (itemName.isBlank()) {
							sendFeedback(context.getSource(), "AutoAuction transfer pair failed: item name is required.");
							return 0;
						}
						if (!modSocketClient.inviteTransfer(receiverUsername, itemName)) {
							sendFeedback(context.getSource(), "AutoAuction transfer pair failed: mod socket is not connected yet.");
							return 0;
						}
						sendFeedback(context.getSource(), "AutoAuction transfer pair requested for " + receiverUsername + " using " + itemName + ".");
						return 1;
					}))));
	}

	private CompletableFuture<Suggestions> suggestTransferReceivers(SuggestionsBuilder builder) {
		refreshTransferAccountSuggestions();
		return suggestStrings(TransferCommandSuggestions.receiverUsernames(transferController.connectedAccountUsernames()), builder);
	}

	private CompletableFuture<Suggestions> suggestPendingTransferSenders(SuggestionsBuilder builder) {
		refreshTransferAccountSuggestions();
		return suggestStrings(TransferCommandSuggestions.receiverUsernames(transferController.pendingSenderUsernames()), builder);
	}

	private CompletableFuture<Suggestions> suggestTransferItems(SuggestionsBuilder builder) {
		return suggestStrings(TransferCommandSuggestions.itemNames(), builder);
	}

	private CompletableFuture<Suggestions> suggestStrings(List<String> values, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (String value : values) {
			if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(value);
			}
		}
		return builder.buildFuture();
	}

	private void refreshTransferAccountSuggestions() {
		if (modSocketClient != null) {
			if (modSocketClient.requestTransferAccounts()) {
				transferAccountListRequests.markSilentRequestSent();
			}
		}
	}

	private int runTransferCommand(FabricClientCommandSource source, OptionalLong targetCoins) {
		if (!transferController.canRunAsSender()) {
			sendFeedback(source, "AutoAuction transfer run failed: this account is not a paired sender.");
			return 0;
		}
		String itemName = transferController.session().itemName();
		if (targetCoins.isPresent()) {
			transferLoopGoal = new TransferLoopGoal(targetCoins.getAsLong());
		} else {
			transferLoopGoal = null;
		}
		requestPreparedTransferRun(source.getClient(), itemName, false);
		if (targetCoins.isPresent()) {
			requestBazaarTransferEstimate(itemName, actions.countInventoryItemsByName(source.getClient(), itemName), targetCoins.getAsLong());
			sendFeedback(source, "AutoAuction transfer target set to " + formatCoins(targetCoins.getAsLong()) + " coins.");
		}
		return 1;
	}

	private void requestPreparedTransferRun(Minecraft client, String itemName, boolean loopContinuation) {
		int quantity = actions.countInventoryItemsByName(client, itemName);
		int parkedStacks = parkedStacksForItem(itemName);
		if (senderParkedStacks > 0 && parkedStacks == 0) {
			sendTransferFeedback("AutoAuction transfer run failed: parked stacks for " + senderParkedItemName + " must be restored before running " + itemName + ".");
			transferLoopGoal = null;
			startSenderEnderChestRestoreIfNeeded(client);
			return;
		}
		if (quantity <= 0 && parkedStacks <= 0) {
			sendTransferFeedback("AutoAuction transfer run failed: no inventory items matching " + itemName + ".");
			transferLoopGoal = null;
			return;
		}
		OptionalLong purse = currentSkyBlockStatus(client).purse();
		if (purse.isEmpty()) {
			sendTransferFeedback("AutoAuction transfer run failed: could not read sender purse for safe quantity calculation.");
			transferLoopGoal = null;
			return;
		}
		String productId = BazaarProductId.fromItemName(itemName);
		sendTransferFeedback("AutoAuction transfer preparing safe stack quantity for " + itemName + "...");
		CompletableFuture
			.supplyAsync(() -> HypixelBazaarClient.fetchQuickStatus(productId))
			.thenAccept(status -> Minecraft.getInstance().execute(() -> {
				if (status.isEmpty()) {
					sendTransferFeedback("AutoAuction transfer run failed: Bazaar price unavailable for " + productId + ".");
					transferLoopGoal = null;
					return;
				}
				double estimatedProfitPerItem = estimatedTransferProfitPerItem(status.get().buyPrice(), status.get().sellPrice());
				long remainingTarget = transferLoopGoal == null ? Long.MAX_VALUE : transferLoopGoal.remainingCoins();
				EnderChestParkingPlan plan = EnderChestParkingPlan.createForPersistentParking(
					quantity,
					parkedStacks,
					purse.getAsLong(),
					0L,
					status.get().buyPrice(),
					estimatedProfitPerItem,
					remainingTarget,
					Integer.MAX_VALUE
				);
				if (!plan.canRun()) {
					sendTransferFeedback("AutoAuction transfer run failed: " + transferPrepareStopReasonMessage(plan.stopReason()) + ".");
					transferLoopGoal = null;
					return;
				}
				if (parkedStacks == plan.requiredParkSlots()) {
					sendTransferFeedback("AutoAuction transfer safe stack quantity ready without EC adjustment for " + itemName + ".");
					if (!sendPreparedTransferRun(Minecraft.getInstance(), itemName, plan.transferQuantity(), loopContinuation)) {
						transferLoopGoal = null;
					}
					return;
				}
				senderPrepareTransferWorkflow = new SenderPrepareTransferWorkflow(
					itemName,
					loopContinuation,
					purse.getAsLong(),
					status.get().buyPrice(),
					estimatedProfitPerItem,
					remainingTarget
				);
				senderPrepareTransferWorkflow.start(Minecraft.getInstance());
			}))
			.exceptionally(error -> {
				sendTransferFeedback("AutoAuction transfer run failed: Bazaar price lookup failed for " + productId + ".");
				transferLoopGoal = null;
				return null;
			});
	}

	private boolean sendPreparedTransferRun(Minecraft client, String itemName, int quantity, boolean loopContinuation) {
		if (!modSocketClient.runTransfer(quantity)) {
			sendTransferFeedback("AutoAuction transfer run failed: mod socket is not connected yet.");
			return false;
		}
		startTransferPurseTracking("sender", client);
		sendTransferFeedback("AutoAuction transfer " + (loopContinuation ? "next cycle" : "run")
			+ " requested for " + quantity + "x " + itemName + ".");
		return true;
	}

	private double estimatedTransferProfitPerItem(double quickBuyPrice, double quickSellPrice) {
		double receiverBuyCost = quickSellPrice + 0.1D;
		double receiverSellRevenue = Math.max(0D, quickBuyPrice - 0.1D) * 0.9875D;
		return Math.max(0D, receiverSellRevenue - receiverBuyCost);
	}

	private int parkedStacksForItem(String itemName) {
		if (senderParkedStacks <= 0 || senderParkedItemName == null || !senderParkedItemName.equalsIgnoreCase(itemName)) {
			return 0;
		}
		return senderParkedStacks;
	}

	private String transferPrepareStopReasonMessage(EnderChestParkingPlan.StopReason reason) {
		return switch (reason) {
			case BELOW_STACK -> "safe transfer quantity is below one stack";
			case NO_PRICE -> "Bazaar buy-back price is unavailable";
			case NOT_ENOUGH_EC_SPACE -> "not enough empty Ender Chest slots to park extra items safely";
			case NONE -> "unknown transfer preparation stop";
		};
	}

	private void startSenderEnderChestRestoreIfNeeded(Minecraft client) {
		if (senderEnderChestRestoreWorkflow != null || senderParkedStacks <= 0 || senderParkedItemName == null || senderParkedItemName.isBlank()) {
			return;
		}
		senderEnderChestRestoreWorkflow = new SenderEnderChestRestoreWorkflow(senderParkedItemName, senderParkedStacks);
		senderEnderChestRestoreWorkflow.start(client);
	}

	private void requestBazaarTransferEstimate(String itemName, int quantity, long targetCoins) {
		String productId = BazaarProductId.fromItemName(itemName);
		CompletableFuture
			.supplyAsync(() -> HypixelBazaarClient.fetchQuickStatus(productId)
				.flatMap(status -> BazaarTransferEstimate.fromPrices(status.productId(), quantity, targetCoins, status.buyPrice(), status.sellPrice())))
			.thenAccept(estimate -> estimate.ifPresentOrElse(
				value -> sendTransferFeedback("AutoAuction transfer estimate for " + value.productId()
					+ ": about " + formatCoins(value.estimatedDeltaPerCycle())
					+ " coins/cycle after tax, about " + value.estimatedCycles()
					+ " cycles for " + formatCoins(value.targetCoins()) + "."),
				() -> sendTransferFeedback("AutoAuction transfer estimate unavailable for " + productId + "; continuing with live purse deltas.")
			))
			.exceptionally(error -> {
				sendTransferFeedback("AutoAuction transfer estimate unavailable for " + productId + "; continuing with live purse deltas.");
				return null;
			});
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

	private DumpCounts dumpScoreboardAndTabList(Minecraft client) {
		List<String> scoreboardLines = readScoreboardLines(client);
		List<String> tabListLines = readTabListLines(client);
		for (String line : StatusDumpFormatter.format(scoreboardLines, tabListLines)) {
			Autoauction.LOGGER.info(line);
		}
		SkyBlockStatus status = SkyBlockStatus.fromLines(scoreboardLines, tabListLines);
		Autoauction.LOGGER.info(
			"AutoAuction parsed status: area={} island={} purse={}",
			status.area().orElse("unknown"),
			status.currentIsland().map(SkyBlockStatus.Island::displayName).orElse("unknown"),
			formatOptionalPurse(status.purse())
		);
		return new DumpCounts(scoreboardLines.size(), tabListLines.size());
	}

	private SkyBlockStatus currentSkyBlockStatus(Minecraft client) {
		return SkyBlockStatus.fromLines(readScoreboardLines(client), readTabListLines(client));
	}

	private String currentIslandMessage(Minecraft client) {
		SkyBlockStatus status = currentSkyBlockStatus(client);
		return "AutoAuction current island: " + status.currentIsland().map(SkyBlockStatus.Island::displayName).orElse("unknown")
			+ " (Area: " + status.area().orElse("unknown") + ")"
			+ ", purse=" + formatOptionalPurse(status.purse()) + ".";
	}

	private OptionalLong currentPurse(Minecraft client) {
		return currentSkyBlockStatus(client).purse();
	}

	private void startTransferPurseTracking(String role, Minecraft client) {
		SkyBlockStatus status = currentSkyBlockStatus(client);
		transferPurseTracker.start(role, status.purse());
		Autoauction.LOGGER.info(
			"AutoAuction transfer {} purse before: {} area={} island={}",
			role,
			formatOptionalPurse(status.purse()),
			status.area().orElse("unknown"),
			status.currentIsland().map(SkyBlockStatus.Island::displayName).orElse("unknown")
		);
	}

	private Optional<TransferPurseTracker.Summary> finishTransferPurseTracking(Minecraft client) {
		Optional<TransferPurseTracker.Summary> result = transferPurseTracker.finish(currentPurse(client));
		result.ifPresent(summary -> {
			String message = "AutoAuction transfer " + summary.role()
				+ " purse before=" + formatCoins(summary.before())
				+ " after=" + formatCoins(summary.after())
				+ " delta=" + formatSignedCoins(summary.delta()) + ".";
			sendDebugFeedback(client, message);
		});
		return result;
	}

	private String formatOptionalPurse(OptionalLong purse) {
		return purse.isPresent() ? formatCoins(purse.getAsLong()) : "unknown";
	}

	private String formatCoins(long coins) {
		return String.format("%,d", coins);
	}

	private String formatSignedCoins(long coins) {
		return (coins >= 0 ? "+" : "") + formatCoins(coins);
	}

	private static String transferStep(String role, String phase, int quantity, String itemName, String action) {
		return TransferDebugMessages.step(role, phase, quantity, itemName, action);
	}

	private static String transferState(String role, String phase, String state, int quantity, String itemName, int delayMs) {
		return TransferDebugMessages.state(role, phase, state, quantity, itemName, Math.max(MIN_CLICK_DELAY_MS, delayMs));
	}

	private boolean isOnPrivateIsland(Minecraft client) {
		return !SkyBlockIslandGuard.needsPrivateIslandWarp(currentSkyBlockStatus(client));
	}

	private boolean sendIslandWarpIfNeeded(Minecraft client, String reason) {
		SkyBlockStatus status = currentSkyBlockStatus(client);
		if (!SkyBlockIslandGuard.needsPrivateIslandWarp(status)) {
			Autoauction.LOGGER.info("AutoAuction already on Private Island for {}.", reason);
			return false;
		}
		Autoauction.LOGGER.info(
			"AutoAuction sending /is for {} from area={}.",
			reason,
			status.area().orElse("unknown")
		);
		actions.sendChatCommand(client, "/is");
		return true;
	}

	private List<String> readScoreboardLines(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		Scoreboard scoreboard = connection != null ? connection.scoreboard() : null;
		if (scoreboard == null && client.level != null) {
			scoreboard = client.level.getScoreboard();
		}
		if (scoreboard == null) {
			return List.of();
		}
		Scoreboard selectedScoreboard = scoreboard;

		Objective objective = selectedScoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return List.of();
		}

		List<String> lines = new ArrayList<>();
		lines.add("title=" + objective.getDisplayName().getString());
		selectedScoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden())
			.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
			.map(entry -> scoreboardLine(selectedScoreboard, entry))
			.filter(line -> !line.isBlank())
			.forEach(lines::add);
		return lines;
	}

	private String scoreboardLine(Scoreboard scoreboard, PlayerScoreEntry entry) {
		Component display = entry.display();
		String text = display == null ? "" : display.getString();
		if (!text.isBlank()) {
			return text;
		}

		PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
		if (team != null) {
			return PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner())).getString();
		}
		return entry.owner();
	}

	private List<String> readTabListLines(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return List.of();
		}

		return connection.getListedOnlinePlayers().stream()
			.sorted(Comparator.comparingInt(PlayerInfo::getTabListOrder)
				.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
			.map(this::tabListLine)
			.filter(line -> !line.isBlank())
			.toList();
	}

	private String tabListLine(PlayerInfo info) {
		Component displayName = info.getTabListDisplayName();
		if (displayName != null) {
			return displayName.getString();
		}
		return info.getProfile().name();
	}

	private String statusMessage() {
		return "AutoAuction status: state=" + controller.state()
			+ ", enabled=" + controller.isEnabled()
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
		if (!config.canRunRealListing()) {
			notifyIssueAsync("macroStopCommand is not configured");
			return;
		}

		startRealWorkflow(client, armor);
	}

	private void startRealWorkflow(Minecraft client, EnumMap<ArmorPiece, ArmorSnapshot> armor) {
		CompletableFuture<List<PricedArmor>> prices = realListingPrices(armor, defaultInventoryNames(armor));
		realWorkflow = new RealAuctionWorkflow(new ArrayList<>(armor.values()), prices);
		realWorkflow.start(client);
	}

	private void beginAccountHandoff(Minecraft client) {
		String previousUsername = client.getUser() == null ? "" : client.getUser().getName();
		AltManagerHandoffClient.HandoffResult result = handoffClient.switchToNextAccount();
		if (!result.switched()) {
			notifyIssueAsync(result.message());
			Autoauction.LOGGER.warn("AutoAuction account handoff skipped: {}", result.message());
			workflowStarted = false;
			controller.start();
			return;
		}

		pendingHandoff = new PendingHandoff(previousUsername, result.targetUsername(), result.targetUuid());
		Autoauction.LOGGER.info(
			"AutoAuction account handoff requested: {} -> {} ({})",
			previousUsername,
			result.targetUsername(),
			result.targetUuid()
		);
	}

	private void startTestListingWorkflow(Minecraft client, EnumMap<ArmorPiece, String> equippedNames) {
		EnumMap<ArmorPiece, ArmorSnapshot> fakeArmor = TestArmorSnapshots.finalDestinationSet(config.killThreshold());
		CompletableFuture<List<PricedArmor>> prices = testListingPrices(fakeArmor, equippedNames);
		realWorkflow = new RealAuctionWorkflow(new ArrayList<>(fakeArmor.values()), prices);
		realWorkflow.start(client);
	}

	static CompletableFuture<List<PricedArmor>> testListingPrices(
		EnumMap<ArmorPiece, ArmorSnapshot> armor,
		EnumMap<ArmorPiece, String> inventoryNames
	) {
		return fixedListingPrices(armor, inventoryNames, TEST_LISTING_PRICE);
	}

	static CompletableFuture<List<PricedArmor>> realListingPrices(
		EnumMap<ArmorPiece, ArmorSnapshot> armor,
		EnumMap<ArmorPiece, String> inventoryNames
	) {
		return fixedListingPrices(armor, inventoryNames, REAL_ARMOR_LISTING_PRICE);
	}

	private static CompletableFuture<List<PricedArmor>> fixedListingPrices(
		EnumMap<ArmorPiece, ArmorSnapshot> armor,
		EnumMap<ArmorPiece, String> inventoryNames,
		int price
	) {
		List<PricedArmor> priced = new ArrayList<>();
		for (ArmorPiece piece : ArmorPiece.values()) {
			ArmorSnapshot snapshot = armor.get(piece);
			String inventoryName = inventoryNames.get(piece);
			if (snapshot == null || inventoryName == null || inventoryName.isBlank()) {
				throw new IllegalStateException("missing armor listing data for " + piece);
			}
			Autoauction.LOGGER.info("AutoAuction fixed listing price for {} at {}", snapshot.baseName(), price);
			priced.add(new PricedArmor(snapshot, inventoryName, price));
		}
		return CompletableFuture.completedFuture(priced);
	}

	private void priceArmorAsync(EnumMap<ArmorPiece, ArmorSnapshot> armor, String completionMessage) {
		CompletableFuture.runAsync(() -> {
			try {
				for (ArmorSnapshot snapshot : armor.values()) {
					var response = apiClient.recommend(requestFactory.fromArmor(snapshot, config.killThreshold()));
					int price = response.recommendedPrice();
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

	private void notifyIssueAsync(String message) {
		CompletableFuture.runAsync(() -> {
			try {
				notifier.issue(message);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction Discord notification failed", e);
			}
		});
	}

	record PricedArmor(ArmorSnapshot armor, String inventoryItemName, int price) {
	}

	record PendingTransferFill(String itemName, int quantity) {
	}

	record PendingTransferSellFill(String itemName, int quantity) {
	}

	record DumpCounts(int scoreboardLines, int tabListLines) {
	}

	private enum ReceiverBuyOrderState {
		OPEN_BAZAAR,
		WAIT_PRIVATE_ISLAND,
		WAIT_ITEM_PAGE,
		CLICK_CREATE_BUY_ORDER,
		WAIT_AMOUNT_SCREEN,
		CLICK_CUSTOM_AMOUNT,
		WAIT_AMOUNT_SIGN,
		SUBMIT_AMOUNT,
		WAIT_PRICE_SCREEN,
		CLICK_TOP_ORDER_PRICE,
		WAIT_CONFIRM_BUY_ORDER,
		CLICK_CONFIRM_BUY_ORDER,
		DONE,
		ERROR
	}

	private final class ReceiverBuyOrderWorkflow {
		private final String itemName;
		private final int quantity;
		private ReceiverBuyOrderState state = ReceiverBuyOrderState.OPEN_BAZAAR;
		private long nextActionAt;
		private long stateStartedAt;

		private ReceiverBuyOrderWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("receiver", "buy-order", quantity, itemName, "start creating buy order"));
			transition(ReceiverBuyOrderState.OPEN_BAZAAR, client);
		}

		private void tick(Minecraft client) {
			if (state == ReceiverBuyOrderState.DONE || state == ReceiverBuyOrderState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_BAZAAR -> {
					if (sendIslandWarpIfNeeded(client, "receiver buy order")) {
						transition(ReceiverBuyOrderState.WAIT_PRIVATE_ISLAND, client, islandCommandCooldownDelayMs());
						return;
					}
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(ReceiverBuyOrderState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_PRIVATE_ISLAND -> {
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(ReceiverBuyOrderState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, transferStep("receiver", "buy-order", quantity, itemName, "click slot " + itemSlot.get() + " Bazaar search result"));
							actions.clickSlot(client, itemSlot.get());
							transition(ReceiverBuyOrderState.WAIT_ITEM_PAGE, client);
							return;
						}
					}
					if (BazaarTransferWorkflow.isItemPage(title, itemName)) {
						transition(ReceiverBuyOrderState.CLICK_CREATE_BUY_ORDER, client);
						return;
					}
					if (!BazaarTransferWorkflow.isItemPage(title, itemName)) {
						timeout(client, config.screenTimeoutMs(), "Bazaar item page did not open for " + itemName);
						return;
					}
				}
				case CLICK_CREATE_BUY_ORDER -> {
					int slot = actions.findHandlerSlotByItemName(client, "Create Buy Order")
						.orElse(BazaarTransferWorkflow.CREATE_BUY_ORDER_SLOT);
					debug(client, transferStep("receiver", "buy-order", quantity, itemName, "click slot " + slot + " Create Buy Order"));
					actions.clickSlot(client, slot);
					transition(ReceiverBuyOrderState.WAIT_AMOUNT_SCREEN, client);
				}
				case WAIT_AMOUNT_SCREEN -> {
					if (!BazaarTransferWorkflow.isBuyOrderAmountScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Buy order amount screen did not open");
						return;
					}
					transition(ReceiverBuyOrderState.CLICK_CUSTOM_AMOUNT, client);
				}
				case CLICK_CUSTOM_AMOUNT -> {
					int slot = actions.findHandlerSlotByItemName(client, "Custom Amount")
						.orElse(BazaarTransferWorkflow.CUSTOM_AMOUNT_SLOT);
					debug(client, transferStep("receiver", "buy-order", quantity, itemName, "click slot " + slot + " Custom Amount"));
					actions.clickSlot(client, slot);
					transition(ReceiverBuyOrderState.WAIT_AMOUNT_SIGN, client);
				}
				case WAIT_AMOUNT_SIGN -> {
					if (!BazaarTransferWorkflow.isSignScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Custom amount sign did not open");
						return;
					}
					transition(ReceiverBuyOrderState.SUBMIT_AMOUNT, client);
				}
				case SUBMIT_AMOUNT -> {
					String amount = BazaarTransferWorkflow.quantityText(quantity);
					debug(client, transferStep("receiver", "buy-order", quantity, itemName, "type custom amount " + amount));
					if (!actions.submitSignText(client, amount)) {
						fail(client, "could not submit custom buy-order amount");
						return;
					}
					transition(ReceiverBuyOrderState.WAIT_PRICE_SCREEN, client);
				}
				case WAIT_PRICE_SCREEN -> {
					if (!BazaarTransferWorkflow.isBuyOrderPriceScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Buy order price screen did not open");
						return;
					}
					transition(ReceiverBuyOrderState.CLICK_TOP_ORDER_PRICE, client);
				}
				case CLICK_TOP_ORDER_PRICE -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Top Order +0.1")
						.orElse(BazaarTransferWorkflow.TOP_ORDER_PLUS_0_1_SLOT);
					debug(client, transferStep("receiver", "buy-order", quantity, itemName, "click slot " + slot + " Top Order +0.1"));
					actions.clickSlot(client, slot);
					transition(ReceiverBuyOrderState.WAIT_CONFIRM_BUY_ORDER, client);
				}
				case WAIT_CONFIRM_BUY_ORDER -> {
					if (!BazaarTransferWorkflow.isConfirmBuyOrderScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Confirm buy order screen did not open");
						return;
					}
					transition(ReceiverBuyOrderState.CLICK_CONFIRM_BUY_ORDER, client);
				}
				case CLICK_CONFIRM_BUY_ORDER -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Buy Order")
						.orElse(BazaarTransferWorkflow.CONFIRM_BUY_ORDER_SLOT);
					debug(client, transferStep("receiver", "buy-order", quantity, itemName, "click slot " + slot + " Confirm Buy Order"));
					actions.clickSlot(client, slot);
					if (!modSocketClient.buyOrderReady(quantity)) {
						fail(client, "could not notify sender that buy order is ready");
						return;
					}
					pendingTransferFill = new PendingTransferFill(itemName, quantity);
					done(client, transferStep("receiver", "buy-order", quantity, itemName, "submitted buy order; waiting for sender instant sell"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(ReceiverBuyOrderState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(ReceiverBuyOrderState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("receiver", "buy-order", next.name(), quantity, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverBuyOrderState.DONE;
			receiverBuyOrderWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = ReceiverBuyOrderState.ERROR;
			receiverBuyOrderWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer receiver workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer failed: " + message));
			}
			notifyIssueAsync("transfer receiver workflow failed: " + message);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private enum ReceiverSellOfferState {
		OPEN_BAZAAR,
		WAIT_PRIVATE_ISLAND,
		WAIT_BAZAAR,
		CLICK_MANAGE_ORDERS,
		WAIT_ORDERS,
		CLICK_FILLED_BUY_ORDER,
		CLOSE_AFTER_CLAIM,
		OPEN_ITEM_PAGE,
		WAIT_ITEM_PAGE,
		CLICK_CREATE_SELL_OFFER,
		WAIT_PRICE_SCREEN,
		CLICK_BEST_OFFER_MINUS,
		WAIT_CONFIRM_SELL_OFFER,
		CLICK_CONFIRM_SELL_OFFER,
		DONE,
		ERROR
	}

	private final class ReceiverSellOfferWorkflow {
		private final String itemName;
		private final int quantity;
		private ReceiverSellOfferState state = ReceiverSellOfferState.OPEN_BAZAAR;
		private long nextActionAt;
		private long stateStartedAt;

		private ReceiverSellOfferWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "buy order filled; claim item and create sell offer"));
			transition(ReceiverSellOfferState.OPEN_BAZAAR, client);
		}

		private void tick(Minecraft client) {
			if (state == ReceiverSellOfferState.DONE || state == ReceiverSellOfferState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_BAZAAR -> {
					if (sendIslandWarpIfNeeded(client, "receiver sell offer")) {
						transition(ReceiverSellOfferState.WAIT_PRIVATE_ISLAND, client, islandCommandCooldownDelayMs());
						return;
					}
					actions.sendChatCommand(client, "/bz");
					transition(ReceiverSellOfferState.WAIT_BAZAAR, client);
				}
				case WAIT_PRIVATE_ISLAND -> {
					actions.sendChatCommand(client, "/bz");
					transition(ReceiverSellOfferState.WAIT_BAZAAR, client);
				}
				case WAIT_BAZAAR -> {
					if (!BazaarTransferWorkflow.isBazaarResultScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Bazaar screen did not open before manage orders");
						return;
					}
					transition(ReceiverSellOfferState.CLICK_MANAGE_ORDERS, client);
				}
				case CLICK_MANAGE_ORDERS -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Manage Orders")
						.orElse(BazaarTransferWorkflow.MANAGE_ORDERS_SLOT);
					debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + slot + " Manage Orders"));
					actions.clickSlot(client, slot);
					transition(ReceiverSellOfferState.WAIT_ORDERS, client);
				}
				case WAIT_ORDERS -> {
					if (!BazaarTransferWorkflow.isOrdersScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Bazaar orders screen did not open");
						return;
					}
					transition(ReceiverSellOfferState.CLICK_FILLED_BUY_ORDER, client);
				}
				case CLICK_FILLED_BUY_ORDER -> {
					int slot = actions.findHandlerSlotByItemName(client, "BUY " + itemName)
						.orElseThrow(() -> new IllegalStateException("filled buy order not found for " + itemName));
					debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + slot + " filled BUY order to claim items"));
					actions.clickSlot(client, slot);
					transition(ReceiverSellOfferState.CLOSE_AFTER_CLAIM, client, bazaarCloseDelayMs());
				}
				case CLOSE_AFTER_CLAIM -> {
					actions.closeScreen(client);
					transition(ReceiverSellOfferState.OPEN_ITEM_PAGE, client, bazaarCloseDelayMs());
				}
				case OPEN_ITEM_PAGE -> {
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(ReceiverSellOfferState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + itemSlot.get() + " Bazaar search result"));
							actions.clickSlot(client, itemSlot.get());
							transition(ReceiverSellOfferState.WAIT_ITEM_PAGE, client);
							return;
						}
					}
					if (BazaarTransferWorkflow.isItemPage(title, itemName)) {
						transition(ReceiverSellOfferState.CLICK_CREATE_SELL_OFFER, client);
						return;
					}
					timeout(client, config.screenTimeoutMs(), "Bazaar item page did not open before sell offer for " + itemName);
				}
				case CLICK_CREATE_SELL_OFFER -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Create Sell Offer")
						.orElse(BazaarTransferWorkflow.CREATE_SELL_OFFER_SLOT);
					debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + slot + " Create Sell Offer"));
					actions.clickSlot(client, slot);
					transition(ReceiverSellOfferState.WAIT_PRICE_SCREEN, client);
				}
				case WAIT_PRICE_SCREEN -> {
					if (!BazaarTransferWorkflow.isSellOfferPriceScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Sell offer price screen did not open");
						return;
					}
					transition(ReceiverSellOfferState.CLICK_BEST_OFFER_MINUS, client);
				}
				case CLICK_BEST_OFFER_MINUS -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Best Offer -0.1")
						.orElse(BazaarTransferWorkflow.BEST_OFFER_MINUS_0_1_SLOT);
					debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + slot + " Best Offer -0.1"));
					actions.clickSlot(client, slot);
					transition(ReceiverSellOfferState.WAIT_CONFIRM_SELL_OFFER, client);
				}
				case WAIT_CONFIRM_SELL_OFFER -> {
					if (!BazaarTransferWorkflow.isConfirmSellOfferScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Confirm sell offer screen did not open");
						return;
					}
					transition(ReceiverSellOfferState.CLICK_CONFIRM_SELL_OFFER, client);
				}
				case CLICK_CONFIRM_SELL_OFFER -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Sell Offer")
						.orElse(BazaarTransferWorkflow.CONFIRM_SELL_OFFER_SLOT);
					debug(client, transferStep("receiver", "sell-offer", quantity, itemName, "click slot " + slot + " Confirm Sell Offer"));
					actions.clickSlot(client, slot);
					pendingTransferSellFill = new PendingTransferSellFill(itemName, quantity);
					if (!modSocketClient.sellOfferReady(quantity)) {
						fail(client, "could not notify sender that sell offer is ready");
						return;
					}
					done(client, transferStep("receiver", "sell-offer", quantity, itemName, "submitted sell offer; waiting for sender instant buy"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(ReceiverSellOfferState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(ReceiverSellOfferState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("receiver", "sell-offer", next.name(), quantity, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverSellOfferState.DONE;
			receiverSellOfferWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = ReceiverSellOfferState.ERROR;
			receiverSellOfferWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer receiver sell-offer workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer receiver sell offer failed: " + message));
			}
			notifyIssueAsync("transfer receiver sell-offer workflow failed: " + message);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private enum SenderInstantSellState {
		OPEN_BAZAAR,
		WAIT_PRIVATE_ISLAND,
		WAIT_ITEM_PAGE,
		CLICK_SELL_INSTANTLY,
		WAIT_WARNING_OR_DONE,
		CLICK_WARNING_CONFIRM,
		DONE,
		ERROR
	}

	private enum SenderPrepareTransferState {
		OPEN_EC,
		WAIT_EC,
		PARK_STACK,
		RESTORE_STACK,
		CLOSE_EC,
		SEND_RUN,
		DONE,
		ERROR
	}

	private enum SenderEnderChestRestoreState {
		OPEN_EC,
		WAIT_EC,
		RESTORE_STACK,
		CLOSE_EC,
		DONE,
		ERROR
	}

	private final class SenderPrepareTransferWorkflow {
		private final String itemName;
		private final boolean loopContinuation;
		private final long senderPurse;
		private final double buyBackPricePerItem;
		private final double estimatedProfitPerItem;
		private final long remainingTargetCoins;
		private SenderPrepareTransferState state = SenderPrepareTransferState.OPEN_EC;
		private EnderChestParkingPlan plan;
		private int desiredParkedStacks;
		private long nextActionAt;
		private long stateStartedAt;

		private SenderPrepareTransferWorkflow(
			String itemName,
			boolean loopContinuation,
			long senderPurse,
			double buyBackPricePerItem,
			double estimatedProfitPerItem,
			long remainingTargetCoins
		) {
			this.itemName = itemName;
			this.loopContinuation = loopContinuation;
			this.senderPurse = Math.max(0L, senderPurse);
			this.buyBackPricePerItem = buyBackPricePerItem;
			this.estimatedProfitPerItem = estimatedProfitPerItem;
			this.remainingTargetCoins = remainingTargetCoins;
		}

		private void start(Minecraft client) {
			debug(client, transferStep("sender", "prepare", 0, itemName, "open Ender Chest for safe stack planning"));
			transition(SenderPrepareTransferState.OPEN_EC, client);
		}

		private void tick(Minecraft client) {
			if (state == SenderPrepareTransferState.DONE || state == SenderPrepareTransferState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_EC -> {
					actions.sendChatCommand(client, "/ec");
					transition(SenderPrepareTransferState.WAIT_EC, client);
				}
				case WAIT_EC -> {
					if (!actions.isEnderChestOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Ender Chest did not open for sender transfer preparation");
						return;
					}
					if (senderParkedStacks > 0 && parkedStacksForItem(itemName) == 0) {
						fail(client, "parked stacks for " + senderParkedItemName + " must be restored before preparing " + itemName);
						return;
					}
					int inventoryQuantity = actions.countInventoryItemsByName(client, itemName);
					int emptyEcSlots = actions.emptyHandlerSlotsInRange(client, EC_STORAGE_FIRST_SLOT, EC_STORAGE_LAST_SLOT).size();
					int currentParkedStacks = parkedStacksForItem(itemName);
					plan = EnderChestParkingPlan.createForPersistentParking(
						inventoryQuantity,
						currentParkedStacks,
						senderPurse,
						0L,
						buyBackPricePerItem,
						estimatedProfitPerItem,
						remainingTargetCoins,
						emptyEcSlots
					);
					if (!plan.canRun()) {
						fail(client, transferPrepareStopReasonMessage(plan.stopReason()));
						return;
					}
					desiredParkedStacks = plan.requiredParkSlots();
					if (desiredParkedStacks > 0 || senderParkedStacks > 0) {
						senderParkedItemName = itemName;
					}
					debug(client, transferStep("sender", "prepare", plan.transferQuantity(), itemName,
						"safe stack plan: transfer " + plan.transferQuantity()
							+ ", desired parked stacks " + desiredParkedStacks
							+ ", current parked stacks " + currentParkedStacks));
					transition(nextAdjustmentState(), client);
				}
				case PARK_STACK -> {
					if (!actions.isEnderChestOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Ender Chest closed while parking sender stacks");
						return;
					}
					if (senderParkedStacks >= desiredParkedStacks) {
						transition(nextAdjustmentState(), client);
						return;
					}
					List<Integer> fullStacks = actions.matchingFullStackHandlerSlotsInRange(client, itemName, PLAYER_INVENTORY_FIRST_SLOT, PLAYER_INVENTORY_LAST_SLOT);
					if (fullStacks.isEmpty()) {
						fail(client, "could not find enough full " + itemName + " stacks to park safely");
						return;
					}
					int slot = fullStacks.getFirst();
					debug(client, transferStep("sender", "prepare", plan.transferQuantity(), itemName, "quick-move extra stack from slot " + slot + " to EC"));
					actions.quickMoveSlot(client, slot);
					senderParkedStacks++;
					senderParkedItemName = itemName;
					transition(nextAdjustmentState(), client);
				}
				case RESTORE_STACK -> {
					if (!actions.isEnderChestOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Ender Chest closed while restoring sender stacks for next run");
						return;
					}
					if (senderParkedStacks <= desiredParkedStacks) {
						transition(nextAdjustmentState(), client);
						return;
					}
					List<Integer> fullStacks = actions.matchingFullStackHandlerSlotsInRange(client, itemName, EC_STORAGE_FIRST_SLOT, EC_STORAGE_LAST_SLOT);
					if (fullStacks.isEmpty()) {
						fail(client, "could not find parked full " + itemName + " stacks in Ender Chest");
						return;
					}
					int slot = fullStacks.getFirst();
					debug(client, transferStep("sender", "prepare", plan.transferQuantity(), itemName, "quick-move parked stack from EC slot " + slot + " to inventory"));
					actions.quickMoveSlot(client, slot);
					senderParkedStacks = Math.max(0, senderParkedStacks - 1);
					if (senderParkedStacks == 0) {
						senderParkedItemName = null;
					}
					transition(nextAdjustmentState(), client);
				}
				case CLOSE_EC -> {
					actions.closeScreen(client);
					transition(SenderPrepareTransferState.SEND_RUN, client);
				}
				case SEND_RUN -> {
					if (!sendPreparedTransferRun(client, itemName, plan.transferQuantity(), loopContinuation)) {
						fail(client, "could not notify receiver to create buy order");
						return;
					}
					done(client, transferStep("sender", "prepare", plan.transferQuantity(), itemName, "receiver buy order requested after EC parking"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private SenderPrepareTransferState nextAdjustmentState() {
			if (senderParkedStacks < desiredParkedStacks) {
				return SenderPrepareTransferState.PARK_STACK;
			}
			if (senderParkedStacks > desiredParkedStacks) {
				return SenderPrepareTransferState.RESTORE_STACK;
			}
			return SenderPrepareTransferState.CLOSE_EC;
		}

		private void transition(SenderPrepareTransferState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(SenderPrepareTransferState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("sender", "prepare", next.name(), plan == null ? 0 : plan.transferQuantity(), itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderPrepareTransferState.DONE;
			senderPrepareTransferWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = SenderPrepareTransferState.ERROR;
			senderPrepareTransferWorkflow = null;
			transferLoopGoal = null;
			Autoauction.LOGGER.error("AutoAuction transfer sender preparation failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer sender prepare failed: " + message));
			}
			notifyIssueAsync("transfer sender prepare failed: " + message);
			startSenderEnderChestRestoreIfNeeded(client);
		}
	}

	private final class SenderEnderChestRestoreWorkflow {
		private final String itemName;
		private int stacksToRestore;
		private SenderEnderChestRestoreState state = SenderEnderChestRestoreState.OPEN_EC;
		private long nextActionAt;
		private long stateStartedAt;

		private SenderEnderChestRestoreWorkflow(String itemName, int stacksToRestore) {
			this.itemName = itemName;
			this.stacksToRestore = Math.max(0, stacksToRestore);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("sender", "restore-ec", stacksToRestore * EnderChestParkingPlan.STACK_SIZE,
				itemName, "restore parked stacks from Ender Chest"));
			transition(SenderEnderChestRestoreState.OPEN_EC, client);
		}

		private void tick(Minecraft client) {
			if (state == SenderEnderChestRestoreState.DONE || state == SenderEnderChestRestoreState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_EC -> {
					if (stacksToRestore <= 0) {
						done(client, transferStep("sender", "restore-ec", 0, itemName, "no parked stacks to restore"));
						return;
					}
					actions.sendChatCommand(client, "/ec");
					transition(SenderEnderChestRestoreState.WAIT_EC, client);
				}
				case WAIT_EC -> {
					if (!actions.isEnderChestOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Ender Chest did not open for sender stack restore");
						return;
					}
					transition(SenderEnderChestRestoreState.RESTORE_STACK, client);
				}
				case RESTORE_STACK -> {
					if (!actions.isEnderChestOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Ender Chest closed while restoring sender stacks");
						return;
					}
					if (stacksToRestore <= 0) {
						transition(SenderEnderChestRestoreState.CLOSE_EC, client);
						return;
					}
					List<Integer> fullStacks = actions.matchingFullStackHandlerSlotsInRange(client, itemName, EC_STORAGE_FIRST_SLOT, EC_STORAGE_LAST_SLOT);
					if (fullStacks.isEmpty()) {
						fail(client, "could not find parked full " + itemName + " stacks in Ender Chest");
						return;
					}
					int slot = fullStacks.getFirst();
					debug(client, transferStep("sender", "restore-ec", stacksToRestore * EnderChestParkingPlan.STACK_SIZE,
						itemName, "quick-move parked stack from EC slot " + slot + " to inventory"));
					actions.quickMoveSlot(client, slot);
					stacksToRestore--;
					senderParkedStacks = Math.max(0, senderParkedStacks - 1);
					transition(stacksToRestore > 0 ? SenderEnderChestRestoreState.RESTORE_STACK : SenderEnderChestRestoreState.CLOSE_EC, client);
				}
				case CLOSE_EC -> {
					actions.closeScreen(client);
					done(client, transferStep("sender", "restore-ec", 0, itemName, "parked stacks restored"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(SenderEnderChestRestoreState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(SenderEnderChestRestoreState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("sender", "restore-ec", next.name(),
				stacksToRestore * EnderChestParkingPlan.STACK_SIZE, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderEnderChestRestoreState.DONE;
			senderEnderChestRestoreWorkflow = null;
			senderParkedItemName = null;
			senderParkedStacks = 0;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = SenderEnderChestRestoreState.ERROR;
			senderEnderChestRestoreWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer sender EC restore failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer sender EC restore failed: " + message));
			}
			notifyIssueAsync("transfer sender EC restore failed: " + message);
		}
	}

	private final class SenderInstantSellWorkflow {
		private final String itemName;
		private final int quantity;
		private SenderInstantSellState state = SenderInstantSellState.OPEN_BAZAAR;
		private long nextActionAt;
		private long stateStartedAt;

		private SenderInstantSellWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("sender", "instant-sell", quantity, itemName, "start selling into receiver buy order"));
			transition(SenderInstantSellState.OPEN_BAZAAR, client);
		}

		private void tick(Minecraft client) {
			if (state == SenderInstantSellState.DONE || state == SenderInstantSellState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_BAZAAR -> {
					if (sendIslandWarpIfNeeded(client, "sender instant sell")) {
						transition(SenderInstantSellState.WAIT_PRIVATE_ISLAND, client, islandCommandCooldownDelayMs());
						return;
					}
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantSellState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_PRIVATE_ISLAND -> {
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantSellState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, transferStep("sender", "instant-sell", quantity, itemName, "click slot " + itemSlot.get() + " Bazaar search result"));
							actions.clickSlot(client, itemSlot.get());
							transition(SenderInstantSellState.WAIT_ITEM_PAGE, client);
							return;
						}
					}
					if (BazaarTransferWorkflow.isItemPage(title, itemName)) {
						transition(SenderInstantSellState.CLICK_SELL_INSTANTLY, client);
						return;
					}
					timeout(client, config.screenTimeoutMs(), "Bazaar item page did not open for sender " + itemName);
				}
				case CLICK_SELL_INSTANTLY -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Sell Instantly")
						.orElse(BazaarTransferWorkflow.SELL_INSTANTLY_SLOT);
					debug(client, transferStep("sender", "instant-sell", quantity, itemName, "click slot " + slot + " Sell Instantly"));
					actions.clickSlot(client, slot);
					transition(SenderInstantSellState.WAIT_WARNING_OR_DONE, client);
				}
				case WAIT_WARNING_OR_DONE -> {
					if (BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						debug(client, transferStep("sender", "instant-sell", quantity, itemName, "warning screen opened; wait 6000ms before confirm"));
						transition(SenderInstantSellState.CLICK_WARNING_CONFIRM, client, INSTANT_SELL_WARNING_DELAY_MS);
						return;
					}
					if (System.currentTimeMillis() - stateStartedAt > INSTANT_SELL_WARNING_GRACE_MS) {
						actions.closeScreen(client);
						done(client, transferStep("sender", "instant-sell", quantity, itemName, "instant sell clicked; waiting for receiver buy-order fill"));
					}
				}
				case CLICK_WARNING_CONFIRM -> {
					if (!BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "instant sell warning screen disappeared before confirm");
						return;
					}
					int slot = actions.findHandlerSlotByExactItemName(client, "WARNING")
						.orElse(BazaarTransferWorkflow.INSTANT_SELL_WARNING_SLOT);
					debug(client, transferStep("sender", "instant-sell", quantity, itemName, "click slot " + slot + " WARNING confirm"));
					actions.clickSlot(client, slot);
					actions.closeScreen(client);
					done(client, transferStep("sender", "instant-sell", quantity, itemName, "confirmed instant sell; waiting for receiver buy-order fill"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(SenderInstantSellState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(SenderInstantSellState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("sender", "instant-sell", next.name(), quantity, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderInstantSellState.DONE;
			senderInstantSellWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = SenderInstantSellState.ERROR;
			senderInstantSellWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer sender workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer sender failed: " + message));
			}
			notifyIssueAsync("transfer sender workflow failed: " + message);
			startSenderEnderChestRestoreIfNeeded(client);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private enum SenderInstantBuyState {
		OPEN_BAZAAR,
		WAIT_PRIVATE_ISLAND,
		WAIT_ITEM_PAGE,
		CLICK_BUY_INSTANTLY,
		WAIT_AMOUNT_SCREEN,
		CLICK_CUSTOM_AMOUNT,
		WAIT_AMOUNT_SIGN,
		SUBMIT_AMOUNT,
		WAIT_CONFIRM_INSTANT_BUY,
		CLICK_CONFIRM_INSTANT_BUY,
		CLICK_BUY_WARNING_CONFIRM,
		WAIT_BUY_COMPLETE,
		DONE,
		ERROR
	}

	private final class SenderInstantBuyWorkflow {
		private final String itemName;
		private final int quantity;
		private SenderInstantBuyState state = SenderInstantBuyState.OPEN_BAZAAR;
		private boolean buyCompleteSeen;
		private int confirmBuyClicks;
		private long nextActionAt;
		private long stateStartedAt;

		private SenderInstantBuyWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("sender", "instant-buy", quantity, itemName, "start buying receiver sell offer"));
			transition(SenderInstantBuyState.OPEN_BAZAAR, client);
		}

		private void tick(Minecraft client) {
			if (state == SenderInstantBuyState.DONE || state == SenderInstantBuyState.ERROR || System.currentTimeMillis() < nextActionAt) {
				return;
			}
			try {
				runState(client);
			} catch (Exception e) {
				fail(client, e.getMessage());
			}
		}

		private void onChatMessage(String message) {
			if (BazaarTransferWorkflow.isInstantBuyCompleteMessage(message, itemName)) {
				buyCompleteSeen = true;
			}
		}

		private void runState(Minecraft client) {
			switch (state) {
				case OPEN_BAZAAR -> {
					if (sendIslandWarpIfNeeded(client, "sender instant buy")) {
						transition(SenderInstantBuyState.WAIT_PRIVATE_ISLAND, client, islandCommandCooldownDelayMs());
						return;
					}
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantBuyState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_PRIVATE_ISLAND -> {
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantBuyState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, transferStep("sender", "instant-buy", quantity, itemName, "click slot " + itemSlot.get() + " Bazaar search result"));
							actions.clickSlot(client, itemSlot.get());
							transition(SenderInstantBuyState.WAIT_ITEM_PAGE, client);
							return;
						}
					}
					if (BazaarTransferWorkflow.isItemPage(title, itemName)) {
						transition(SenderInstantBuyState.CLICK_BUY_INSTANTLY, client);
						return;
					}
					timeout(client, config.screenTimeoutMs(), "Bazaar item page did not open for sender instant buy " + itemName);
				}
				case CLICK_BUY_INSTANTLY -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Buy Instantly")
						.orElse(BazaarTransferWorkflow.BUY_INSTANTLY_SLOT);
					debug(client, transferStep("sender", "instant-buy", quantity, itemName, "click slot " + slot + " Buy Instantly"));
					actions.clickSlot(client, slot);
					transition(SenderInstantBuyState.WAIT_AMOUNT_SCREEN, client);
				}
				case WAIT_AMOUNT_SCREEN -> {
					if (!BazaarTransferWorkflow.isInstantBuyAmountScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "instant-buy amount screen did not open");
						return;
					}
					transition(SenderInstantBuyState.CLICK_CUSTOM_AMOUNT, client);
				}
				case CLICK_CUSTOM_AMOUNT -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Custom Amount")
						.orElse(BazaarTransferWorkflow.CUSTOM_AMOUNT_SLOT);
					debug(client, transferStep("sender", "instant-buy", quantity, itemName, "click slot " + slot + " Custom Amount"));
					actions.clickSlot(client, slot);
					transition(SenderInstantBuyState.WAIT_AMOUNT_SIGN, client);
				}
				case WAIT_AMOUNT_SIGN -> {
					if (!BazaarTransferWorkflow.isSignScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "instant-buy custom amount sign did not open");
						return;
					}
					transition(SenderInstantBuyState.SUBMIT_AMOUNT, client);
				}
				case SUBMIT_AMOUNT -> {
					String amount = BazaarTransferWorkflow.quantityText(quantity);
					debug(client, transferStep("sender", "instant-buy", quantity, itemName, "type custom amount " + amount));
					if (!actions.submitSignText(client, amount)) {
						fail(client, "could not submit custom instant-buy amount");
						return;
					}
					transition(SenderInstantBuyState.WAIT_CONFIRM_INSTANT_BUY, client);
				}
				case WAIT_CONFIRM_INSTANT_BUY -> {
					if (!BazaarTransferWorkflow.isConfirmInstantBuyScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Confirm Instant Buy screen did not open");
						return;
					}
					transition(SenderInstantBuyState.CLICK_CONFIRM_INSTANT_BUY, client);
				}
				case CLICK_CONFIRM_INSTANT_BUY -> {
					int slot = BazaarTransferWorkflow.CONFIRM_INSTANT_BUY_SLOT;
					debug(client, transferStep("sender", "instant-buy", quantity, itemName, "click slot " + slot + " Confirm Instant Buy"));
					actions.clickSlot(client, slot);
					confirmBuyClicks++;
					transition(SenderInstantBuyState.WAIT_BUY_COMPLETE, client);
				}
				case CLICK_BUY_WARNING_CONFIRM -> {
					if (!BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "instant buy warning screen disappeared before confirm");
						return;
					}
					int slot = actions.findHandlerSlotByExactItemName(client, "WARNING")
						.orElse(BazaarTransferWorkflow.INSTANT_SELL_WARNING_SLOT);
					debug(client, transferStep("sender", "instant-buy", quantity, itemName, "click slot " + slot + " WARNING confirm"));
					actions.clickSlot(client, slot);
					transition(SenderInstantBuyState.WAIT_BUY_COMPLETE, client);
				}
				case WAIT_BUY_COMPLETE -> {
					if (!buyCompleteSeen) {
						String title = screenTitle(client);
						if (BazaarTransferWorkflow.isInstantSellWarningScreen(title)) {
							debug(client, transferStep("sender", "instant-buy", quantity, itemName, "warning screen opened; wait 6000ms before confirm"));
							transition(SenderInstantBuyState.CLICK_BUY_WARNING_CONFIRM, client, INSTANT_SELL_WARNING_DELAY_MS);
							return;
						}
						if (BazaarTransferWorkflow.isConfirmInstantBuyScreen(title)
							&& confirmBuyClicks < INSTANT_BUY_CONFIRM_MAX_CLICKS
							&& System.currentTimeMillis() - stateStartedAt > INSTANT_BUY_CONFIRM_RETRY_DELAY_MS) {
							debug(client, transferStep("sender", "instant-buy", quantity, itemName,
								"confirm screen still open; retry Confirm Instant Buy click " + (confirmBuyClicks + 1)));
							transition(SenderInstantBuyState.CLICK_CONFIRM_INSTANT_BUY, client);
							return;
						}
						timeout(client, config.screenTimeoutMs(), "instant-buy completion chat did not arrive");
						return;
					}
					actions.closeScreen(client);
					if (!modSocketClient.sellOfferBought(quantity)) {
						fail(client, "could not notify receiver that sell offer was bought");
						return;
					}
					finishTransferPurseTracking(client);
					done(client, transferStep("sender", "instant-buy", quantity, itemName, "bought receiver sell offer; receiver will claim coins"));
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(SenderInstantBuyState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(SenderInstantBuyState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("sender", "instant-buy", next.name(), quantity, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderInstantBuyState.DONE;
			senderInstantBuyWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = SenderInstantBuyState.ERROR;
			senderInstantBuyWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer sender instant-buy workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer sender instant buy failed: " + message));
			}
			notifyIssueAsync("transfer sender instant-buy workflow failed: " + message);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private enum ReceiverClaimSellOfferState {
		OPEN_BAZAAR,
		WAIT_PRIVATE_ISLAND,
		WAIT_BAZAAR,
		CLICK_MANAGE_ORDERS,
		WAIT_ORDERS,
		CLICK_FILLED_SELL_OFFER,
		CLOSE_AFTER_CLAIM,
		WAIT_PURSE_UPDATE,
		DONE,
		ERROR
	}

	private final class ReceiverClaimSellOfferWorkflow {
		private final String itemName;
		private final int quantity;
		private ReceiverClaimSellOfferState state = ReceiverClaimSellOfferState.OPEN_BAZAAR;
		private long nextActionAt;
		private long stateStartedAt;

		private ReceiverClaimSellOfferWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, transferStep("receiver", "claim-coins", quantity, itemName, "start claiming filled sell offer"));
			transition(ReceiverClaimSellOfferState.OPEN_BAZAAR, client);
		}

		private void tick(Minecraft client) {
			if (state == ReceiverClaimSellOfferState.DONE || state == ReceiverClaimSellOfferState.ERROR || System.currentTimeMillis() < nextActionAt) {
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
				case OPEN_BAZAAR -> {
					if (sendIslandWarpIfNeeded(client, "receiver sell-offer claim")) {
						transition(ReceiverClaimSellOfferState.WAIT_PRIVATE_ISLAND, client, islandCommandCooldownDelayMs());
						return;
					}
					actions.sendChatCommand(client, "/bz");
					transition(ReceiverClaimSellOfferState.WAIT_BAZAAR, client);
				}
				case WAIT_PRIVATE_ISLAND -> {
					actions.sendChatCommand(client, "/bz");
					transition(ReceiverClaimSellOfferState.WAIT_BAZAAR, client);
				}
				case WAIT_BAZAAR -> {
					if (!BazaarTransferWorkflow.isBazaarResultScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Bazaar screen did not open before claiming sell offer");
						return;
					}
					transition(ReceiverClaimSellOfferState.CLICK_MANAGE_ORDERS, client);
				}
				case CLICK_MANAGE_ORDERS -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Manage Orders")
						.orElse(BazaarTransferWorkflow.MANAGE_ORDERS_SLOT);
					debug(client, transferStep("receiver", "claim-coins", quantity, itemName, "click slot " + slot + " Manage Orders"));
					actions.clickSlot(client, slot);
					transition(ReceiverClaimSellOfferState.WAIT_ORDERS, client);
				}
				case WAIT_ORDERS -> {
					if (!BazaarTransferWorkflow.isOrdersScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "Bazaar orders screen did not open before claiming sell offer");
						return;
					}
					transition(ReceiverClaimSellOfferState.CLICK_FILLED_SELL_OFFER, client);
				}
				case CLICK_FILLED_SELL_OFFER -> {
					int slot = actions.findHandlerSlotByItemName(client, "SELL " + itemName)
						.or(() -> actions.findHandlerSlotByExactItemName(client, "Claim All Coins"))
						.orElseThrow(() -> new IllegalStateException("filled sell offer not found for " + itemName));
					debug(client, transferStep("receiver", "claim-coins", quantity, itemName, "click slot " + slot + " filled SELL offer / Claim All Coins"));
					actions.clickSlot(client, slot);
					transition(ReceiverClaimSellOfferState.CLOSE_AFTER_CLAIM, client, bazaarCloseDelayMs());
				}
				case CLOSE_AFTER_CLAIM -> {
					actions.closeScreen(client);
					transition(ReceiverClaimSellOfferState.WAIT_PURSE_UPDATE, client);
				}
				case WAIT_PURSE_UPDATE -> {
					Optional<TransferPurseTracker.Summary> preview = transferPurseTracker.preview(currentPurse(client));
					if (preview.isPresent() && preview.get().delta() >= 0) {
						Optional<TransferPurseTracker.Summary> summary = finishTransferPurseTracking(client);
						long delta = summary.map(TransferPurseTracker.Summary::delta).orElse(0L);
						if (!modSocketClient.cycleComplete(quantity, summary.map(TransferPurseTracker.Summary::before).orElse(0L), summary.map(TransferPurseTracker.Summary::after).orElse(0L), delta)) {
							fail(client, "could not notify sender that receiver cycle completed");
							return;
						}
						done(client, transferStep("receiver", "claim-coins", quantity, itemName, "coins claimed; sent cycle complete with delta " + formatSignedCoins(delta)));
						return;
					}
					if (preview.isPresent()) {
						TransferPurseTracker.Summary summary = preview.get();
						debug(client, TransferDebugMessages.purseWait("receiver", "claim-coins", summary.before(), summary.after(), summary.delta()));
					}
					timeout(client, config.screenTimeoutMs(), "receiver purse did not update after claiming sell offer");
				}
				case DONE, ERROR -> {
				}
			}
		}

		private void transition(ReceiverClaimSellOfferState next, Minecraft client) {
			transition(next, client, config.clickDelayMs());
		}

		private void transition(ReceiverClaimSellOfferState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info(transferState("receiver", "claim-coins", next.name(), quantity, itemName, delayMs));
			}
		}

		private void debug(Minecraft client, String message) {
			sendDebugFeedback(client, message);
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverClaimSellOfferState.DONE;
			receiverClaimSellOfferWorkflow = null;
			sendDebugFeedback(client, message);
		}

		private void fail(Minecraft client, String message) {
			state = ReceiverClaimSellOfferState.ERROR;
			receiverClaimSellOfferWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer receiver claim workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer receiver claim failed: " + message));
			}
			notifyIssueAsync("transfer receiver claim workflow failed: " + message);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private static final class PendingHandoff {
		private final String previousUsername;
		private final String targetUsername;
		private final String targetUuid;
		private final long startedAt = System.currentTimeMillis();
		private long proxyReadyAt;
		private boolean reconnectStarted;
		private long reconnectStartedAt;
		private long hypixelReadyAt;

		private PendingHandoff(String previousUsername, String targetUsername, String targetUuid) {
			this.previousUsername = previousUsername;
			this.targetUsername = targetUsername;
			this.targetUuid = targetUuid;
		}
	}

	private enum RealAuctionState {
		STOP_MACRO,
		RETURN_TO_ISLAND_BEFORE_ARMOR,
		WAIT_ISLAND_BEFORE_ARMOR,
		CHECK_INVENTORY_SPACE,
		OPEN_BAZAAR_TO_FREE_SPACE,
		WAIT_BAZAAR_TO_FREE_SPACE,
		CLICK_SELL_INVENTORY,
		WAIT_SELL_INVENTORY_CONFIRM,
		CONFIRM_SELL_INVENTORY,
		WAIT_AFTER_SELL_INVENTORY,
		CLOSE_BAZAAR_AFTER_SELL,
		WAIT_AFTER_BAZAAR_CLOSE,
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
					NebulaMacroController.EnsureResult result = macroController.ensureOff(
						command -> runMacroToggleCommand(client, command),
						System.currentTimeMillis()
					);
					if (result == NebulaMacroController.EnsureResult.PENDING) {
						return;
					}
					if (result == NebulaMacroController.EnsureResult.FAILED) {
						fail(client, "macro disable confirmation timed out");
						return;
					}
					debug(client, "Nebula combat macro confirmed disabled.");
					transition(RealAuctionState.RETURN_TO_ISLAND_BEFORE_ARMOR, client);
				}
				case RETURN_TO_ISLAND_BEFORE_ARMOR -> {
					if (isOnPrivateIsland(client)) {
						debug(client, "Already on Private Island before removing armor.");
						transitionNow(RealAuctionState.CHECK_INVENTORY_SPACE, client);
						return;
					}
					debug(client, "Returning to island before removing armor.");
					sendIslandWarpIfNeeded(client, "armor removal");
					transition(RealAuctionState.WAIT_ISLAND_BEFORE_ARMOR, client, islandCommandCooldownDelayMs());
				}
				case WAIT_ISLAND_BEFORE_ARMOR -> {
					transitionNow(RealAuctionState.CHECK_INVENTORY_SPACE, client);
				}
				case CHECK_INVENTORY_SPACE -> {
					if (actions.hasEmptyInventorySlots(client, armor.size())) {
						debug(client, "Inventory has enough space for armor removal.");
						transition(RealAuctionState.OPEN_INVENTORY, client);
						return;
					}

					debug(client, "Inventory is full before armor removal. Opening Bazaar to sell inventory.");
					transition(RealAuctionState.OPEN_BAZAAR_TO_FREE_SPACE, client);
				}
				case OPEN_BAZAAR_TO_FREE_SPACE -> {
					actions.sendChatCommand(client, "/bz");
					transition(RealAuctionState.WAIT_BAZAAR_TO_FREE_SPACE, client);
				}
				case WAIT_BAZAAR_TO_FREE_SPACE -> {
					if (!actions.isBazaarOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Bazaar screen did not open before inventory sell");
						return;
					}
					transition(RealAuctionState.CLICK_SELL_INVENTORY, client);
				}
				case CLICK_SELL_INVENTORY -> {
					int slot = actions.findSellInventoryButtonSlot(client)
						.orElseThrow(() -> new IllegalStateException("could not find Bazaar Sell Inventory Now button"));
					debug(client, "Clicking Bazaar Sell Inventory Now from slot " + slot + ".");
					actions.clickSlot(client, slot);
					transition(RealAuctionState.WAIT_SELL_INVENTORY_CONFIRM, client);
				}
				case WAIT_SELL_INVENTORY_CONFIRM -> {
					if (!actions.isSellInventoryConfirmOpen(client)) {
						timeout(client, config.screenTimeoutMs(), "Bazaar sell inventory confirmation did not open");
						return;
					}
					transition(RealAuctionState.CONFIRM_SELL_INVENTORY, client);
				}
				case CONFIRM_SELL_INVENTORY -> {
					int slot = actions.findSellInventoryConfirmSlot(client)
						.orElseThrow(() -> new IllegalStateException("could not find Bazaar sell inventory confirm button"));
					debug(client, "Confirming Bazaar inventory sell from slot " + slot + ".");
					actions.clickSlot(client, slot);
					transition(RealAuctionState.WAIT_AFTER_SELL_INVENTORY, client, 1_500);
				}
				case WAIT_AFTER_SELL_INVENTORY -> {
					if (actions.hasEmptyInventorySlots(client, armor.size())) {
						debug(client, "Bazaar inventory sell finished; closing menu before armor removal.");
						transition(RealAuctionState.CLOSE_BAZAAR_AFTER_SELL, client);
						return;
					}
					timeout(client, config.screenTimeoutMs(), "not enough empty inventory slots after Bazaar inventory sell");
				}
				case CLOSE_BAZAAR_AFTER_SELL -> {
					actions.closeScreen(client);
					transition(RealAuctionState.WAIT_AFTER_BAZAAR_CLOSE, client, bazaarCloseDelayMs());
				}
				case WAIT_AFTER_BAZAAR_CLOSE -> {
					debug(client, "Bazaar menu closed. Opening inventory to remove armor.");
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
						fail(client, "not enough empty inventory slots to remove armor after pre-check");
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
						debug(client, "Listing price for " + item.inventoryItemName() + ": "
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
					debug(client, "Typing listing price: " + priceText + " (" + currentItem().price() + " coins).");
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
					beginAccountHandoff(client);
					actions.disconnect(client, "AutoAuction finished listing.");
					transition(RealAuctionState.DONE, client);
					realWorkflow = null;
					notifyInfoAsync("real auction workflow finished; account handoff requested.");
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
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info("AutoAuction real workflow state: {}", next);
			}
		}

		private void transitionNow(RealAuctionState next, Minecraft client) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt;
			if (isDebugEnabled()) {
				Autoauction.LOGGER.info("AutoAuction real workflow state: {}", next);
			}
		}

		private void delay() {
			nextActionAt = System.currentTimeMillis() + effectiveClickDelayMs();
		}

		private int effectiveClickDelayMs() {
			return AutoauctionClient.effectiveClickDelayMs(config.clickDelayMs());
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
			sendDebugFeedback(client, message);
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

	private void notifyBanAsync(String username, String reason) {
		CompletableFuture.runAsync(() -> {
			try {
				notifier.ban(username, reason);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction Discord ban notification failed", e);
			}
		});
	}

	private record ResolvedMacroKey(OptionalInt keyCode, String source) {
	}

	static int effectiveClickDelayMs(int configuredDelayMs) {
		return Math.max(configuredDelayMs, MIN_CLICK_DELAY_MS);
	}

	static boolean proxyReadyDelayElapsed(long proxyReadyAt, long now) {
		return proxyReadyAt > 0L && now - proxyReadyAt >= PROXY_READY_RECONNECT_DELAY_MS;
	}

	static boolean hypixelReadyDelayElapsed(long hypixelReadyAt, long now) {
		return hypixelReadyAt > 0L && now - hypixelReadyAt >= HYPIXEL_READY_MACRO_DELAY_MS;
	}

	static int islandCommandCooldownDelayMs() {
		return ISLAND_COMMAND_COOLDOWN_DELAY_MS;
	}

	static int bazaarCloseDelayMs() {
		return BAZAAR_CLOSE_DELAY_MS;
	}

	static String retoggleStatusMessage(int count, NebulaMacroController.ObservedState observedState, boolean desiredOn) {
		return "AutoAuction retoggle status: count=" + count
			+ ", observed=" + observedState
			+ ", desired=" + (desiredOn ? "ON" : "OFF")
			+ ".";
	}
}
