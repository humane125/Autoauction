package com.autoauction.client;

import com.autoauction.Autoauction;
import com.autoauction.client.api.AuctionApiClient;
import com.autoauction.client.automation.AuctionAutomationController;
import com.autoauction.client.automation.AutomationState;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.config.AutoAuctionConfigStore;
import com.autoauction.client.control.ModSocketClient;
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
import com.autoauction.client.minecraft.MinecraftGameActions;
import com.autoauction.client.notify.DiscordNotifier;
import com.autoauction.client.transfer.BazaarTransferWorkflow;
import com.autoauction.client.transfer.TransferController;
import com.autoauction.client.transfer.TransferPurseTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class AutoauctionClient implements ClientModInitializer {
	private static final int TEST_LISTING_PRICE = 2_500_000;
	private static final int MIN_CLICK_DELAY_MS = 400;
	private static final long PROXY_READY_RECONNECT_DELAY_MS = 1_500L;
	private static final long HYPIXEL_READY_MACRO_DELAY_MS = 500L;
	private static final int ISLAND_COMMAND_COOLDOWN_DELAY_MS = 5_500;
	private static final int BAZAAR_CLOSE_DELAY_MS = 1_000;
	private static final int INSTANT_SELL_WARNING_DELAY_MS = 6_000;
	private static final int INSTANT_SELL_WARNING_GRACE_MS = 1_500;

	private AutoAuctionConfig config;
	private AuctionAutomationController controller;
	private MinecraftGameActions actions;
	private AuctionApiClient apiClient;
	private ModSocketClient modSocketClient;
	private TransferController transferController;
	private final TransferPurseTracker transferPurseTracker = new TransferPurseTracker();
	private DiscordNotifier notifier;
	private AltManagerHandoffClient handoffClient;
	private final AuctionItemRequestFactory requestFactory = new AuctionItemRequestFactory();
	private int tickCounter;
	private boolean workflowStarted;
	private boolean dumpSlotsKeyWasDown;
	private boolean emergencyStopKeyWasDown;
	private long lastCookieBuffAlertAt;
	private RealAuctionWorkflow realWorkflow;
	private ReceiverBuyOrderWorkflow receiverBuyOrderWorkflow;
	private ReceiverSellOfferWorkflow receiverSellOfferWorkflow;
	private SenderInstantSellWorkflow senderInstantSellWorkflow;
	private SenderInstantBuyWorkflow senderInstantBuyWorkflow;
	private ReceiverClaimSellOfferWorkflow receiverClaimSellOfferWorkflow;
	private PendingTransferFill pendingTransferFill;
	private PendingTransferSellFill pendingTransferSellFill;
	private PendingHandoff pendingHandoff;

	@Override
	public void onInitializeClient() {
		try {
			this.config = new AutoAuctionConfigStore(FabricLoader.getInstance().getConfigDir()).load();
			this.controller = new AuctionAutomationController(config);
			this.actions = new MinecraftGameActions();
			this.apiClient = new AuctionApiClient(config);
			this.transferController = new TransferController();
			this.modSocketClient = new ModSocketClient(config, this::disconnectFromRemoteCommand, transferSocketHandler());
			this.notifier = new DiscordNotifier(config);
			this.handoffClient = new AltManagerHandoffClient();
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
				handlePendingHandoff(client);
				if (client.player == null || controller == null || actions == null) {
					return;
				}

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
				if (senderInstantSellWorkflow != null) {
					senderInstantSellWorkflow.tick(client);
				}
				if (senderInstantBuyWorkflow != null) {
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

		if (!config.macroStartCommand().isBlank()) {
			Autoauction.LOGGER.info("AutoAuction handoff starting macro with command: {}", config.macroStartCommand());
			actions.sendClientCommand(client, config.macroStartCommand());
		} else {
			Autoauction.LOGGER.info("AutoAuction handoff complete; macroStartCommand is not configured.");
		}
		controller.start();
		workflowStarted = false;
		notifyInfoAsync("account handoff complete for " + currentUsername + ".");
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
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (AuctionBlockedMessageDetector.isCookieBuffRequired(text)) {
				handleCookieBuffRequired();
			}
			handleTransferBuyOrderFilled(text);
			handleTransferSellOfferFilled(text);
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
				sendTransferFeedback(transferController.showAccounts(connectedAccounts));
			}

			@Override
			public void onInvite(ModSocketClient.TransferSession session) {
				sendTransferFeedback(transferController.incomingInvite(toTransferSession(session)));
			}

			@Override
			public void onPending(ModSocketClient.TransferSession session) {
				sendTransferFeedback(transferController.outgoingPending(toTransferSession(session)));
			}

			@Override
			public void onAccepted(ModSocketClient.TransferSession session, String role) {
				sendTransferFeedback(transferController.accepted(toTransferSession(session), transferRole(role)));
			}

			@Override
			public void onDeclined(ModSocketClient.TransferSession session, String reason) {
				sendTransferFeedback(transferController.declined(reason));
			}

			@Override
			public void onCancelled(String sessionId, String reason) {
				sendTransferFeedback(transferController.cancelled(reason));
				receiverBuyOrderWorkflow = null;
				receiverSellOfferWorkflow = null;
				senderInstantSellWorkflow = null;
				senderInstantBuyWorkflow = null;
				receiverClaimSellOfferWorkflow = null;
				pendingTransferFill = null;
				pendingTransferSellFill = null;
				transferPurseTracker.clear();
			}

			@Override
			public void onRun(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
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
				sendTransferFeedback("AutoAuction transfer buy order ready: selling " + run.quantity() + "x " + session.itemName() + ".");
				Minecraft.getInstance().execute(() -> {
					senderInstantSellWorkflow = new SenderInstantSellWorkflow(session.itemName(), run.quantity());
					senderInstantSellWorkflow.start(Minecraft.getInstance());
				});
			}

			@Override
			public void onSellOfferReady(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendTransferFeedback("AutoAuction transfer sell offer ready: buying " + run.quantity() + "x " + session.itemName() + ".");
				Minecraft.getInstance().execute(() -> {
					senderInstantBuyWorkflow = new SenderInstantBuyWorkflow(session.itemName(), run.quantity());
					senderInstantBuyWorkflow.start(Minecraft.getInstance());
				});
			}

			@Override
			public void onSellOfferBought(ModSocketClient.TransferRun run) {
				TransferController.Session session = toTransferSession(run.session());
				sendTransferFeedback("AutoAuction transfer sell offer bought: claiming coins for " + run.quantity() + "x " + session.itemName() + ".");
				pendingTransferSellFill = null;
				startReceiverClaimSellOfferWorkflow(session.itemName(), run.quantity());
			}

			@Override
			public void onError(String code, String message) {
				sendTransferFeedback(transferController.error(code, message));
				receiverBuyOrderWorkflow = null;
				receiverSellOfferWorkflow = null;
				senderInstantSellWorkflow = null;
				senderInstantBuyWorkflow = null;
				receiverClaimSellOfferWorkflow = null;
				pendingTransferFill = null;
				pendingTransferSellFill = null;
				transferPurseTracker.clear();
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
				client.player.sendSystemMessage(Component.literal(message));
			}
		});
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
			}))
			.then(literal("transfer")
				.then(literal("list").executes(context -> {
					if (!modSocketClient.requestTransferAccounts()) {
						sendFeedback(context.getSource(), "AutoAuction transfer list failed: mod socket is not connected yet.");
						return 0;
					}
					sendFeedback(context.getSource(), "AutoAuction transfer account list requested.");
					return 1;
				}))
				.then(literal("accept")
					.then(argument("senderUsername", StringArgumentType.word()).executes(context -> {
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
					.then(argument("senderUsername", StringArgumentType.word()).executes(context -> {
						String senderUsername = StringArgumentType.getString(context, "senderUsername");
						if (!modSocketClient.declineTransfer(senderUsername)) {
							sendFeedback(context.getSource(), "AutoAuction transfer decline failed: mod socket is not connected yet.");
							return 0;
						}
						sendFeedback(context.getSource(), "AutoAuction transfer decline sent for " + senderUsername + ".");
						return 1;
					})))
				.then(literal("run").executes(context -> {
					if (!transferController.canRunAsSender()) {
						sendFeedback(context.getSource(), "AutoAuction transfer run failed: this account is not a paired sender.");
						return 0;
					}
					String itemName = transferController.session().itemName();
					int quantity = actions.countInventoryItemsByName(context.getSource().getClient(), itemName);
					if (quantity <= 0) {
						sendFeedback(context.getSource(), "AutoAuction transfer run failed: no inventory items matching " + itemName + ".");
						return 0;
					}
					if (!modSocketClient.runTransfer(quantity)) {
						sendFeedback(context.getSource(), "AutoAuction transfer run failed: mod socket is not connected yet.");
						return 0;
					}
					startTransferPurseTracking("sender", context.getSource().getClient());
					sendFeedback(context.getSource(), "AutoAuction transfer run requested for " + quantity + "x " + itemName + ".");
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
				.then(argument("receiverUsername", StringArgumentType.word())
					.then(argument("itemName", StringArgumentType.greedyString()).executes(context -> {
						String receiverUsername = StringArgumentType.getString(context, "receiverUsername");
						String itemName = StringArgumentType.getString(context, "itemName").trim();
						if (itemName.isBlank()) {
							sendFeedback(context.getSource(), "AutoAuction transfer invite failed: item name is required.");
							return 0;
						}
						if (!modSocketClient.inviteTransfer(receiverUsername, itemName)) {
							sendFeedback(context.getSource(), "AutoAuction transfer invite failed: mod socket is not connected yet.");
							return 0;
						}
						sendFeedback(context.getSource(), "AutoAuction transfer invite requested for " + receiverUsername + " using " + itemName + ".");
						return 1;
					}))))
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

	private void finishTransferPurseTracking(Minecraft client) {
		transferPurseTracker.finish(currentPurse(client)).ifPresent(summary -> {
			String message = "AutoAuction transfer " + summary.role()
				+ " purse before=" + formatCoins(summary.before())
				+ " after=" + formatCoins(summary.after())
				+ " delta=" + formatSignedCoins(summary.delta()) + ".";
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		});
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
		CompletableFuture<List<PricedArmor>> prices = priceArmorForRealAsync(armor, defaultInventoryNames(armor));
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
		List<PricedArmor> priced = new ArrayList<>();
		for (ArmorPiece piece : ArmorPiece.values()) {
			ArmorSnapshot snapshot = armor.get(piece);
			String inventoryName = inventoryNames.get(piece);
			if (snapshot == null || inventoryName == null || inventoryName.isBlank()) {
				throw new IllegalStateException("missing armor test data for " + piece);
			}
			priced.add(new PricedArmor(snapshot, inventoryName, TEST_LISTING_PRICE));
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
			debug(client, "AutoAuction transfer receiver workflow started for " + quantity + "x " + itemName + ".");
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
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(ReceiverBuyOrderState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, "Clicking Bazaar result slot " + itemSlot.get() + " for " + itemName + ".");
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
					debug(client, "Clicking Create Buy Order slot " + slot + " for " + itemName + ".");
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
					debug(client, "Clicking Custom Amount slot " + slot + " for buy order.");
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
					debug(client, "Submitting transfer buy-order amount: " + amount + ".");
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
					debug(client, "Clicking Top Order +0.1 slot " + slot + " for buy order.");
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
					debug(client, "Clicking Confirm Buy Order slot " + slot + " for " + quantity + "x " + itemName + ".");
					actions.clickSlot(client, slot);
					if (!modSocketClient.buyOrderReady(quantity)) {
						fail(client, "could not notify sender that buy order is ready");
						return;
					}
					pendingTransferFill = new PendingTransferFill(itemName, quantity);
					done(client, "AutoAuction transfer receiver submitted buy order for " + quantity + "x " + itemName + "; waiting for fill.");
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
			Autoauction.LOGGER.info("AutoAuction transfer receiver workflow state: {}", next);
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverBuyOrderState.DONE;
			receiverBuyOrderWorkflow = null;
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
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
			debug(client, "AutoAuction transfer receiver detected filled buy order for " + quantity + "x " + itemName + ".");
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
					debug(client, "Clicking Manage Orders slot " + slot + ".");
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
					debug(client, "Clicking filled buy order slot " + slot + " for " + itemName + ".");
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
							debug(client, "Clicking Bazaar result slot " + itemSlot.get() + " for receiver sell offer " + itemName + ".");
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
					debug(client, "Clicking Create Sell Offer slot " + slot + " for " + itemName + ".");
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
					debug(client, "Clicking Best Offer -0.1 slot " + slot + ".");
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
					debug(client, "Clicking Confirm Sell Offer slot " + slot + " for " + quantity + "x " + itemName + ".");
					actions.clickSlot(client, slot);
					pendingTransferSellFill = new PendingTransferSellFill(itemName, quantity);
					if (!modSocketClient.sellOfferReady(quantity)) {
						fail(client, "could not notify sender that sell offer is ready");
						return;
					}
					done(client, "AutoAuction transfer receiver submitted sell offer for " + quantity + "x " + itemName + ".");
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
			Autoauction.LOGGER.info("AutoAuction transfer receiver sell-offer workflow state: {}", next);
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverSellOfferState.DONE;
			receiverSellOfferWorkflow = null;
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
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
		WAIT_ITEM_PAGE,
		CLICK_SELL_INSTANTLY,
		WAIT_WARNING_OR_DONE,
		CLICK_WARNING_CONFIRM,
		DONE,
		ERROR
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
			debug(client, "AutoAuction transfer sender workflow started for " + quantity + "x " + itemName + ".");
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
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantSellState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, "Clicking Bazaar result slot " + itemSlot.get() + " for sender " + itemName + ".");
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
					debug(client, "Clicking Sell Instantly slot " + slot + " for " + itemName + ".");
					actions.clickSlot(client, slot);
					transition(SenderInstantSellState.WAIT_WARNING_OR_DONE, client);
				}
				case WAIT_WARNING_OR_DONE -> {
					if (BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						debug(client, "Instant sell warning opened; waiting 6 seconds before confirming.");
						transition(SenderInstantSellState.CLICK_WARNING_CONFIRM, client, INSTANT_SELL_WARNING_DELAY_MS);
						return;
					}
					if (System.currentTimeMillis() - stateStartedAt > INSTANT_SELL_WARNING_GRACE_MS) {
						actions.closeScreen(client);
						done(client, "AutoAuction transfer sender clicked Sell Instantly for " + quantity + "x " + itemName + ".");
					}
				}
				case CLICK_WARNING_CONFIRM -> {
					if (!BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						timeout(client, config.screenTimeoutMs(), "instant sell warning screen disappeared before confirm");
						return;
					}
					int slot = actions.findHandlerSlotByExactItemName(client, "WARNING")
						.orElse(BazaarTransferWorkflow.INSTANT_SELL_WARNING_SLOT);
					debug(client, "Clicking instant sell WARNING slot " + slot + ".");
					actions.clickSlot(client, slot);
					actions.closeScreen(client);
					done(client, "AutoAuction transfer sender confirmed instant sell for " + quantity + "x " + itemName + ".");
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
			Autoauction.LOGGER.info("AutoAuction transfer sender workflow state: {}", next);
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderInstantSellState.DONE;
			senderInstantSellWorkflow = null;
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void fail(Minecraft client, String message) {
			state = SenderInstantSellState.ERROR;
			senderInstantSellWorkflow = null;
			Autoauction.LOGGER.error("AutoAuction transfer sender workflow failed: {}", message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("AutoAuction transfer sender failed: " + message));
			}
			notifyIssueAsync("transfer sender workflow failed: " + message);
		}

		private String screenTitle(Minecraft client) {
			return client.screen == null ? "" : client.screen.getTitle().getString();
		}
	}

	private enum SenderInstantBuyState {
		OPEN_BAZAAR,
		WAIT_ITEM_PAGE,
		CLICK_BUY_INSTANTLY,
		WAIT_AMOUNT_SCREEN,
		CLICK_CUSTOM_AMOUNT,
		WAIT_AMOUNT_SIGN,
		SUBMIT_AMOUNT,
		WAIT_CONFIRM_INSTANT_BUY,
		CLICK_CONFIRM_INSTANT_BUY,
		DONE,
		ERROR
	}

	private final class SenderInstantBuyWorkflow {
		private final String itemName;
		private final int quantity;
		private SenderInstantBuyState state = SenderInstantBuyState.OPEN_BAZAAR;
		private long nextActionAt;
		private long stateStartedAt;

		private SenderInstantBuyWorkflow(String itemName, int quantity) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
		}

		private void start(Minecraft client) {
			debug(client, "AutoAuction transfer sender instant-buy workflow started for " + quantity + "x " + itemName + ".");
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

		private void runState(Minecraft client) {
			switch (state) {
				case OPEN_BAZAAR -> {
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(SenderInstantBuyState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							debug(client, "Clicking Bazaar result slot " + itemSlot.get() + " for sender instant buy " + itemName + ".");
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
					debug(client, "Clicking Buy Instantly slot " + slot + " for " + itemName + ".");
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
					debug(client, "Clicking Custom Amount slot " + slot + " for instant buy.");
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
					debug(client, "Submitting transfer instant-buy amount: " + amount + ".");
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
					int slot = actions.findHandlerSlotByExactItemName(client, "Custom Amount")
						.orElse(BazaarTransferWorkflow.CONFIRM_INSTANT_BUY_SLOT);
					debug(client, "Clicking Confirm Instant Buy slot " + slot + " for " + quantity + "x " + itemName + ".");
					actions.clickSlot(client, slot);
					actions.closeScreen(client);
					if (!modSocketClient.sellOfferBought(quantity)) {
						fail(client, "could not notify receiver that sell offer was bought");
						return;
					}
					finishTransferPurseTracking(client);
					done(client, "AutoAuction transfer sender bought receiver sell offer for " + quantity + "x " + itemName + ".");
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
			Autoauction.LOGGER.info("AutoAuction transfer sender instant-buy workflow state: {}", next);
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = SenderInstantBuyState.DONE;
			senderInstantBuyWorkflow = null;
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
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
		WAIT_BAZAAR,
		CLICK_MANAGE_ORDERS,
		WAIT_ORDERS,
		CLICK_FILLED_SELL_OFFER,
		CLOSE_AFTER_CLAIM,
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
			debug(client, "AutoAuction transfer receiver claim workflow started for " + quantity + "x " + itemName + ".");
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
					debug(client, "Clicking Manage Orders slot " + slot + " to claim sell offer.");
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
					debug(client, "Clicking filled sell offer slot " + slot + " for " + itemName + ".");
					actions.clickSlot(client, slot);
					transition(ReceiverClaimSellOfferState.CLOSE_AFTER_CLAIM, client, bazaarCloseDelayMs());
				}
				case CLOSE_AFTER_CLAIM -> {
					actions.closeScreen(client);
					finishTransferPurseTracking(client);
					done(client, "AutoAuction transfer receiver claimed sell offer coins for " + quantity + "x " + itemName + ".");
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
			Autoauction.LOGGER.info("AutoAuction transfer receiver claim workflow state: {}", next);
		}

		private void debug(Minecraft client, String message) {
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
		}

		private void timeout(Minecraft client, int timeoutMs, String message) {
			if (System.currentTimeMillis() - stateStartedAt > timeoutMs) {
				fail(client, message);
			}
		}

		private void done(Minecraft client, String message) {
			state = ReceiverClaimSellOfferState.DONE;
			receiverClaimSellOfferWorkflow = null;
			Autoauction.LOGGER.info(message);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(message));
			}
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
					debug(client, "Stopping macro with command: " + config.macroStopCommand());
					actions.sendClientCommand(client, config.macroStopCommand());
					transition(RealAuctionState.RETURN_TO_ISLAND_BEFORE_ARMOR, client);
				}
				case RETURN_TO_ISLAND_BEFORE_ARMOR -> {
					debug(client, "Returning to island before removing armor.");
					actions.sendChatCommand(client, "/is");
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
			Autoauction.LOGGER.info("AutoAuction real workflow state: {}", next);
		}

		private void transitionNow(RealAuctionState next, Minecraft client) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt;
			Autoauction.LOGGER.info("AutoAuction real workflow state: {}", next);
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

	private void notifyBanAsync(String username, String reason) {
		CompletableFuture.runAsync(() -> {
			try {
				notifier.ban(username, reason);
			} catch (Exception e) {
				Autoauction.LOGGER.error("AutoAuction Discord ban notification failed", e);
			}
		});
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
}
