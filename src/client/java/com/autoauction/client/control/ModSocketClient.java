package com.autoauction.client.control;

import com.autoauction.Autoauction;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ModAccountStatusDetector;
import com.autoauction.client.stats.AccountStatsSnapshot;
import com.autoauction.client.stats.SummoningEyeEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ModSocketClient implements AutoCloseable {
	private static final Gson GSON = new Gson();
	private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000;
	private static final long DEFAULT_RECONNECT_INITIAL_DELAY_MS = 100;
	private static final long DEFAULT_RECONNECT_MAX_DELAY_MS = 30_000;
	private static final long DEFAULT_CONNECT_ATTEMPT_TIMEOUT_MS = 15_000;

	private final AutoAuctionConfig config;
	private final Transport transport;
	private final long heartbeatIntervalMs;
	private final long reconnectInitialDelayMs;
	private final long reconnectMaxDelayMs;
	private final long connectAttemptTimeoutMs;
	private final ScheduledExecutorService heartbeatExecutor;
	private final Consumer<String> logSink;
	private final Consumer<String> disconnectHandler;
	private final TransferHandler transferHandler;
	private final ScreenshotHandler screenshotHandler;
	private final RemoteActionHandler remoteActionHandler;
	private final RegisteredAccountsHandler registeredAccountsHandler;

	private Connection connection;
	private ScheduledFuture<?> heartbeatTask;
	private ScheduledFuture<?> reconnectTask;
	private ScheduledFuture<?> connectTimeoutTask;
	private boolean authenticated;
	private String lastReportedStatus;
	private String connectedUsername;
	private String connectingUsername;
	private String desiredUsername;
	private String desiredClientVersion;
	private String desiredStatus = "active";
	private ModAccountStatusDetector.BanDetails desiredBanDetails;
	private long reconnectDelayMs;
	private long connectionGeneration;
	private boolean closed;

	public ModSocketClient(AutoAuctionConfig config) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS, message -> Autoauction.LOGGER.info(message), reason -> {});
	}

	public ModSocketClient(AutoAuctionConfig config, Consumer<String> disconnectHandler) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS, message -> Autoauction.LOGGER.info(message), disconnectHandler);
	}

	public ModSocketClient(AutoAuctionConfig config, Consumer<String> disconnectHandler, TransferHandler transferHandler) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS, message -> Autoauction.LOGGER.info(message), disconnectHandler, transferHandler);
	}

	public ModSocketClient(AutoAuctionConfig config, Consumer<String> disconnectHandler, TransferHandler transferHandler, ScreenshotHandler screenshotHandler) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS, message -> Autoauction.LOGGER.info(message), disconnectHandler, transferHandler, screenshotHandler);
	}

	public ModSocketClient(
		AutoAuctionConfig config,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler,
		RemoteActionHandler remoteActionHandler
	) {
		this(config, disconnectHandler, transferHandler, screenshotHandler, remoteActionHandler, RegisteredAccountsHandler.NOOP);
	}

	public ModSocketClient(
		AutoAuctionConfig config,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler,
		RemoteActionHandler remoteActionHandler,
		RegisteredAccountsHandler registeredAccountsHandler
	) {
		this(
			config,
			new JavaWebSocketTransport(),
			DEFAULT_HEARTBEAT_INTERVAL_MS,
			message -> Autoauction.LOGGER.info(message),
			disconnectHandler,
			transferHandler,
			screenshotHandler,
			remoteActionHandler,
			registeredAccountsHandler
		);
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs) {
		this(config, transport, heartbeatIntervalMs, message -> Autoauction.LOGGER.info(message), reason -> {});
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs, Consumer<String> logSink) {
		this(config, transport, heartbeatIntervalMs, logSink, reason -> {});
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs, Consumer<String> logSink, Consumer<String> disconnectHandler) {
		this(config, transport, heartbeatIntervalMs, logSink, disconnectHandler, TransferHandler.NOOP);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		Consumer<String> logSink,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler
	) {
		this(config, transport, heartbeatIntervalMs, logSink, disconnectHandler, transferHandler, ScreenshotHandler.NOOP);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		Consumer<String> logSink,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler
	) {
		this(config, transport, heartbeatIntervalMs, logSink, disconnectHandler, transferHandler, screenshotHandler, RemoteActionHandler.NOOP);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		Consumer<String> logSink,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler,
		RemoteActionHandler remoteActionHandler
	) {
		this(
			config,
			transport,
			heartbeatIntervalMs,
			logSink,
			disconnectHandler,
			transferHandler,
			screenshotHandler,
			remoteActionHandler,
			RegisteredAccountsHandler.NOOP
		);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		Consumer<String> logSink,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler,
		RemoteActionHandler remoteActionHandler,
		RegisteredAccountsHandler registeredAccountsHandler
	) {
		this(
			config,
			transport,
			heartbeatIntervalMs,
			DEFAULT_RECONNECT_INITIAL_DELAY_MS,
			DEFAULT_RECONNECT_MAX_DELAY_MS,
			DEFAULT_CONNECT_ATTEMPT_TIMEOUT_MS,
			logSink,
			disconnectHandler,
			transferHandler,
			screenshotHandler,
			remoteActionHandler,
			registeredAccountsHandler
		);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		long reconnectInitialDelayMs,
		long reconnectMaxDelayMs,
		long connectAttemptTimeoutMs,
		Consumer<String> logSink
	) {
		this(
			config,
			transport,
			heartbeatIntervalMs,
			reconnectInitialDelayMs,
			reconnectMaxDelayMs,
			connectAttemptTimeoutMs,
			logSink,
			reason -> {},
			TransferHandler.NOOP,
			ScreenshotHandler.NOOP,
			RemoteActionHandler.NOOP,
			RegisteredAccountsHandler.NOOP
		);
	}

	ModSocketClient(
		AutoAuctionConfig config,
		Transport transport,
		long heartbeatIntervalMs,
		long reconnectInitialDelayMs,
		long reconnectMaxDelayMs,
		long connectAttemptTimeoutMs,
		Consumer<String> logSink,
		Consumer<String> disconnectHandler,
		TransferHandler transferHandler,
		ScreenshotHandler screenshotHandler,
		RemoteActionHandler remoteActionHandler,
		RegisteredAccountsHandler registeredAccountsHandler
	) {
		this.config = config;
		this.transport = transport;
		this.heartbeatIntervalMs = heartbeatIntervalMs;
		this.reconnectInitialDelayMs = Math.max(1, reconnectInitialDelayMs);
		this.reconnectMaxDelayMs = Math.max(this.reconnectInitialDelayMs, reconnectMaxDelayMs);
		this.connectAttemptTimeoutMs = Math.max(1, connectAttemptTimeoutMs);
		this.reconnectDelayMs = this.reconnectInitialDelayMs;
		this.logSink = logSink;
		this.disconnectHandler = disconnectHandler;
		this.transferHandler = transferHandler == null ? TransferHandler.NOOP : transferHandler;
		this.screenshotHandler = screenshotHandler == null ? ScreenshotHandler.NOOP : screenshotHandler;
		this.remoteActionHandler = remoteActionHandler == null ? RemoteActionHandler.NOOP : remoteActionHandler;
		this.registeredAccountsHandler = registeredAccountsHandler == null ? RegisteredAccountsHandler.NOOP : registeredAccountsHandler;
		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "autoauction-mod-socket");
			thread.setDaemon(true);
			return thread;
		});
	}

	public synchronized boolean start(String username, String clientVersion) {
		String cleanUsername = String.valueOf(username == null ? "" : username).trim();
		if (connection != null || connectingUsername != null) {
			return false;
		}
		if (config.apiToken().isBlank() || cleanUsername.isBlank()) {
			log("AutoAuction mod socket not started: API token missing or username missing");
			return false;
		}

		closed = false;
		desiredUsername = cleanUsername;
		desiredClientVersion = String.valueOf(clientVersion == null ? "" : clientVersion);
		cancelReconnect();
		return connectNow(cleanUsername, desiredClientVersion);
	}

	private synchronized boolean connectNow(String username, String clientVersion) {
		if (connection != null || connectingUsername != null || closed) {
			return false;
		}
		URI uri = socketUri(config.apiBaseUrl());
		log("AutoAuction mod socket connecting to " + uri);
		long generation = ++connectionGeneration;
		connectingUsername = username;
		scheduleConnectTimeout(generation);
		CompletableFuture<Connection> connectionFuture = transport.connect(uri, new Listener() {
			@Override
			public void onOpen(Connection openedConnection) {
				synchronized (ModSocketClient.this) {
					if (generation != connectionGeneration) {
						openedConnection.close();
						return;
					}
					connection = openedConnection;
					connectedUsername = username;
					connectingUsername = null;
					reconnectDelayMs = reconnectInitialDelayMs;
					cancelConnectTimeout();
					cancelReconnect();
				}
				openedConnection.send(GSON.toJson(new AuthMessage(config.apiToken(), username, clientVersion)));
				log("AutoAuction mod socket opened; sent auth for " + username + " clientVersion=" + clientVersion);
			}

			@Override
			public void onText(String text) {
				handleMessage(text);
			}

			@Override
			public void onClose() {
				stopHeartbeat();
				log("AutoAuction mod socket closed by remote");
				handleUnexpectedDisconnect(generation);
			}

			@Override
			public void onError(Throwable error) {
				stopHeartbeat();
				log("AutoAuction mod socket error: " + error.getMessage());
				handleUnexpectedDisconnect(generation);
			}
		});
		connectionFuture.whenComplete((ignored, error) -> {
			if (error != null) {
				log("AutoAuction mod socket connect failed: " + error.getMessage());
				handleUnexpectedDisconnect(generation);
			}
		});
		return true;
	}

	public synchronized boolean ensureStartedFor(String username, String clientVersion) {
		String cleanUsername = String.valueOf(username == null ? "" : username).trim();
		if (config.apiToken().isBlank() || cleanUsername.isBlank()) {
			log("AutoAuction mod socket not started: API token missing or username missing");
			return false;
		}
		if (connection != null && Objects.equals(connectedUsername, cleanUsername)) {
			return false;
		}
		if (connection == null && Objects.equals(connectingUsername, cleanUsername)) {
			return false;
		}
		if (connection == null && reconnectTask != null && !reconnectTask.isCancelled() && Objects.equals(desiredUsername, cleanUsername)) {
			return false;
		}
		if (connection != null) {
			closeCurrentConnection();
		}
		return start(cleanUsername, clientVersion);
	}

	private void handleMessage(String text) {
		JsonObject message = GSON.fromJson(text, JsonObject.class);
		if (message == null || !message.has("type")) {
			log("AutoAuction mod socket received malformed message");
			return;
		}
		String type = message.get("type").getAsString();
		log("AutoAuction mod socket received " + type);
		if (Objects.equals(type, "auth_ok")) {
			sendDesiredStatus();
			requestRegisteredAccounts();
			startHeartbeat();
			return;
		}
		if (Objects.equals(type, "disconnect_now")) {
			String reason = stringProperty(message, "reason", "AutoAuction remote disconnect requested.");
			log("AutoAuction mod socket received disconnect_now: " + reason);
			disconnectHandler.accept(reason);
			return;
		}
		if (Objects.equals(type, "request_screenshot")) {
			screenshotHandler.onRequest(new ScreenshotRequest(intProperty(message, "accountId", 0), stringProperty(message, "requestId", "")));
			return;
		}
		if (Objects.equals(type, "remote_action")) {
			remoteActionHandler.onAction(new RemoteAction(
				intProperty(message, "accountId", 0),
				stringProperty(message, "requestId", ""),
				stringProperty(message, "actionType", ""),
				stringProperty(message, "content", "")
			));
			return;
		}
		if (Objects.equals(type, "transfer_accounts")) {
			transferHandler.onAccounts(transferAccounts(message));
			return;
		}
		if (Objects.equals(type, "registered_accounts")) {
			registeredAccountsHandler.onAccounts(registeredAccounts(message));
			return;
		}
		if (Objects.equals(type, "transfer_invite")) {
			transferHandler.onInvite(transferSession(message));
			return;
		}
		if (Objects.equals(type, "transfer_pending")) {
			transferHandler.onPending(transferSession(message));
			return;
		}
		if (Objects.equals(type, "transfer_accepted")) {
			transferHandler.onAccepted(transferSession(message), stringProperty(message, "role", ""));
			return;
		}
		if (Objects.equals(type, "transfer_declined")) {
			transferHandler.onDeclined(transferSession(message), stringProperty(message, "reason", "declined"));
			return;
		}
		if (Objects.equals(type, "transfer_cancelled")) {
			transferHandler.onCancelled(stringProperty(message, "sessionId", ""), stringProperty(message, "reason", "cancelled"));
			return;
		}
		if (Objects.equals(type, "transfer_run")) {
			transferHandler.onRun(transferRun(message));
			return;
		}
		if (Objects.equals(type, "transfer_run_sent")) {
			transferHandler.onRunSent(transferRun(message));
			return;
		}
		if (Objects.equals(type, "transfer_buy_order_ready")) {
			transferHandler.onBuyOrderReady(transferRun(message));
			return;
		}
		if (Objects.equals(type, "transfer_sell_offer_ready")) {
			transferHandler.onSellOfferReady(transferRun(message));
			return;
		}
		if (Objects.equals(type, "transfer_sell_offer_bought")) {
			transferHandler.onSellOfferBought(transferRun(message));
			return;
		}
		if (Objects.equals(type, "transfer_cycle_complete")) {
			transferHandler.onCycleComplete(transferCycle(message));
			return;
		}
		if (Objects.equals(type, "transfer_error")) {
			transferHandler.onError(stringProperty(message, "code", "unknown"), stringProperty(message, "message", "Transfer failed"));
		}
	}

	private List<TransferAccount> transferAccounts(JsonObject message) {
		List<TransferAccount> accounts = new ArrayList<>();
		if (!message.has("accounts") || !message.get("accounts").isJsonArray()) {
			return accounts;
		}
		JsonArray array = message.getAsJsonArray("accounts");
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject account = element.getAsJsonObject();
			accounts.add(new TransferAccount(
				stringProperty(account, "minecraftUsername", ""),
				stringProperty(account, "status", "")
			));
		}
		return accounts;
	}

	private List<RegisteredAccount> registeredAccounts(JsonObject message) {
		List<RegisteredAccount> accounts = new ArrayList<>();
		if (!message.has("accounts") || !message.get("accounts").isJsonArray()) {
			return accounts;
		}
		JsonArray array = message.getAsJsonArray("accounts");
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject account = element.getAsJsonObject();
			accounts.add(new RegisteredAccount(
				stringProperty(account, "minecraftUsername", ""),
				stringProperty(account, "status", "")
			));
		}
		return accounts;
	}

	private TransferSession transferSession(JsonObject message) {
		JsonObject session = message.has("session") && message.get("session").isJsonObject()
			? message.getAsJsonObject("session")
			: new JsonObject();
		return new TransferSession(
			stringProperty(session, "id", ""),
			stringProperty(session, "senderUsername", ""),
			stringProperty(session, "receiverUsername", ""),
			stringProperty(session, "itemName", "")
		);
	}

	private TransferRun transferRun(JsonObject message) {
		return new TransferRun(transferSession(message), intProperty(message, "quantity", 1));
	}

	private TransferCycle transferCycle(JsonObject message) {
		long before = longProperty(message, "before", 0);
		long after = longProperty(message, "after", before);
		return new TransferCycle(transferSession(message), intProperty(message, "quantity", 1), before, after, longProperty(message, "delta", after - before));
	}

	private String stringProperty(JsonObject message, String name, String fallback) {
		if (!message.has(name) || message.get(name).isJsonNull()) {
			return fallback;
		}
		String value = message.get(name).getAsString();
		return value == null || value.isBlank() ? fallback : value;
	}

	private int intProperty(JsonObject message, String name, int fallback) {
		if (!message.has(name) || message.get(name).isJsonNull()) {
			return fallback;
		}
		try {
			return Math.max(1, message.get(name).getAsInt());
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private long longProperty(JsonObject message, String name, long fallback) {
		if (!message.has(name) || message.get(name).isJsonNull()) {
			return fallback;
		}
		try {
			return message.get(name).getAsLong();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public synchronized boolean reportStatus(String status) {
		String cleanStatus = String.valueOf(status == null ? "" : status).trim();
		if (cleanStatus.isBlank()) {
			return false;
		}
		desiredStatus = cleanStatus;
		desiredBanDetails = null;
		if (!authenticated || Objects.equals(lastReportedStatus, cleanStatus)) {
			return false;
		}
		return sendStatus(cleanStatus);
	}

	public synchronized boolean reportBan(ModAccountStatusDetector.BanDetails details) {
		desiredStatus = "banned";
		desiredBanDetails = details;
		if (!authenticated || Objects.equals(lastReportedStatus, "banned")) {
			return false;
		}
		return sendBan(details);
	}

	public synchronized boolean requestTransferAccounts() {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_list");
		return sendTransferMessage(message);
	}

	public synchronized boolean inviteTransfer(String receiverUsername, String itemName) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_invite");
		message.addProperty("receiverUsername", String.valueOf(receiverUsername == null ? "" : receiverUsername).trim());
		message.addProperty("itemName", String.valueOf(itemName == null ? "" : itemName).trim());
		return sendTransferMessage(message);
	}

	public synchronized boolean acceptTransfer(String senderUsername) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_accept");
		message.addProperty("senderUsername", String.valueOf(senderUsername == null ? "" : senderUsername).trim());
		return sendTransferMessage(message);
	}

	public synchronized boolean declineTransfer(String senderUsername) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_decline");
		message.addProperty("senderUsername", String.valueOf(senderUsername == null ? "" : senderUsername).trim());
		return sendTransferMessage(message);
	}

	public synchronized boolean cancelTransfer() {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_cancel");
		return sendTransferMessage(message);
	}

	public synchronized boolean switchTransfer() {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_switch");
		return sendTransferMessage(message);
	}

	public synchronized boolean runTransfer(int quantity) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_run");
		message.addProperty("quantity", Math.max(1, quantity));
		return sendTransferMessage(message);
	}

	public synchronized boolean buyOrderReady(int quantity) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_buy_order_ready");
		message.addProperty("quantity", Math.max(1, quantity));
		return sendTransferMessage(message);
	}

	public synchronized boolean sellOfferReady(int quantity) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_sell_offer_ready");
		message.addProperty("quantity", Math.max(1, quantity));
		return sendTransferMessage(message);
	}

	public synchronized boolean sellOfferBought(int quantity) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_sell_offer_bought");
		message.addProperty("quantity", Math.max(1, quantity));
		return sendTransferMessage(message);
	}

	public synchronized boolean cycleComplete(int quantity, long delta) {
		return cycleComplete(quantity, 0, delta, delta);
	}

	public synchronized boolean cycleComplete(int quantity, long before, long after, long delta) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "transfer_cycle_complete");
		message.addProperty("quantity", Math.max(1, quantity));
		message.addProperty("before", before);
		message.addProperty("after", after);
		message.addProperty("delta", delta);
		return sendTransferMessage(message);
	}

	public synchronized boolean sendClientScreenshot(String imageMime, String imageBase64, String capturedAt) {
		if (!authenticated || connection == null) {
			return false;
		}
		String cleanImageBase64 = String.valueOf(imageBase64 == null ? "" : imageBase64).trim();
		if (cleanImageBase64.isBlank()) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "client_screenshot");
		message.addProperty("imageMime", cleanImageMime(imageMime));
		message.addProperty("imageBase64", cleanImageBase64);
		String cleanCapturedAt = String.valueOf(capturedAt == null ? "" : capturedAt).trim();
		if (!cleanCapturedAt.isBlank()) {
			message.addProperty("capturedAt", cleanCapturedAt);
		}
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent client_screenshot");
		return true;
	}

	public synchronized boolean sendClientLog(String level, String messageText) {
		return sendClientLog(level, "system", messageText);
	}

	public synchronized boolean sendClientLog(String level, String source, String messageText) {
		return sendClientLog(level, source, messageText, List.of());
	}

	public synchronized boolean sendClientLog(String level, String source, String messageText, List<RemoteLogSegment> segments) {
		if (!authenticated || connection == null) {
			return false;
		}
		String cleanMessage = sanitizeRemoteLogMessage(messageText);
		if (cleanMessage.isBlank()) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "client_log");
		message.addProperty("level", cleanLogLevel(level));
		message.addProperty("source", cleanLogSource(source));
		message.addProperty("message", cleanMessage);
		JsonArray cleanSegments = cleanLogSegments(segments);
		if (!cleanSegments.isEmpty()) {
			message.add("segments", cleanSegments);
		}
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent client_log");
		return true;
	}

	public synchronized boolean sendAccountStats(AccountStatsSnapshot snapshot) {
		if (!authenticated || connection == null || snapshot == null) {
			return false;
		}
		JsonObject kills = new JsonObject();
		addOptionalStat(kills, "helmet", snapshot.helmetKills());
		addOptionalStat(kills, "chestplate", snapshot.chestplateKills());
		addOptionalStat(kills, "leggings", snapshot.leggingsKills());
		addOptionalStat(kills, "boots", snapshot.bootsKills());
		JsonObject message = new JsonObject();
		message.addProperty("type", "account_stats");
		message.addProperty("purse", Math.max(0L, snapshot.purse()));
		message.addProperty("macroing", snapshot.macroing());
		message.add("finalDestinationKills", kills);
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent account_stats");
		return true;
	}

	public synchronized boolean sendSummoningEyeEvent(SummoningEyeEvent event) {
		if (!authenticated || connection == null || event == null || event.action().isBlank()) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "summoning_eye_event");
		message.addProperty("action", event.action());
		message.addProperty("quantity", Math.max(1, event.quantity()));
		if (event.pricePerEye() > 0) {
			message.addProperty("pricePerEye", event.pricePerEye());
		}
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent summoning_eye_event");
		return true;
	}

	private static void addOptionalStat(JsonObject object, String name, Integer value) {
		if (value != null) {
			object.addProperty(name, Math.max(0, value));
		}
	}

	private synchronized boolean sendTransferMessage(JsonObject message) {
		if (!authenticated || connection == null) {
			return false;
		}
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent " + message.get("type").getAsString());
		return true;
	}

	private synchronized boolean requestRegisteredAccounts() {
		if (!authenticated || connection == null) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "registered_accounts");
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket requested registered_accounts");
		return true;
	}

	private synchronized boolean sendStatus(String status) {
		if (connection == null) {
			return false;
		}
		connection.send(GSON.toJson(new StatusMessage(status)));
		log("AutoAuction mod socket sent " + status);
		authenticated = true;
		lastReportedStatus = status;
		return true;
	}

	private synchronized boolean sendDesiredStatus() {
		if (Objects.equals(desiredStatus, "banned") && desiredBanDetails != null) {
			return sendBan(desiredBanDetails);
		}
		return sendStatus(String.valueOf(desiredStatus == null || desiredStatus.isBlank() ? "active" : desiredStatus));
	}

	private synchronized boolean sendBan(ModAccountStatusDetector.BanDetails details) {
		if (connection == null) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "banned");
		if (details != null) {
			if (details.reason() != null) {
				message.addProperty("banReason", details.reason());
			}
			if (details.banId() != null) {
				message.addProperty("banId", details.banId());
			}
			if (details.banUntil() != null) {
				message.addProperty("banUntil", details.banUntil());
			}
			if (details.durationMs() > 0) {
				message.addProperty("banDurationMs", details.durationMs());
			}
		}
		connection.send(GSON.toJson(message));
		log("AutoAuction mod socket sent banned");
		authenticated = true;
		lastReportedStatus = "banned";
		return true;
	}

	private synchronized void startHeartbeat() {
		if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
			log("AutoAuction mod socket heartbeat already running");
			return;
		}
		log("AutoAuction mod socket heartbeat started every " + heartbeatIntervalMs + "ms");
		heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
			Connection current;
			synchronized (ModSocketClient.this) {
				current = connection;
			}
			if (current != null) {
				current.send("{\"type\":\"heartbeat\"}");
				log("AutoAuction mod socket sent heartbeat");
			}
		}, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
	}

	private synchronized void stopHeartbeat() {
		if (heartbeatTask != null) {
			heartbeatTask.cancel(false);
			heartbeatTask = null;
			log("AutoAuction mod socket heartbeat stopped");
		}
	}

	private synchronized void handleUnexpectedDisconnect(long generation) {
		if (generation != connectionGeneration) {
			return;
		}
		cancelConnectTimeout();
		connection = null;
		authenticated = false;
		connectedUsername = null;
		connectingUsername = null;
		scheduleReconnect();
	}

	private synchronized void scheduleConnectTimeout(long generation) {
		cancelConnectTimeout();
		connectTimeoutTask = heartbeatExecutor.schedule(() -> {
			synchronized (ModSocketClient.this) {
				if (closed || generation != connectionGeneration || connection != null || connectingUsername == null) {
					return;
				}
				log("AutoAuction mod socket connect timed out after " + connectAttemptTimeoutMs + "ms");
				connectionGeneration++;
				authenticated = false;
				connectedUsername = null;
				connectingUsername = null;
				scheduleReconnect();
			}
		}, connectAttemptTimeoutMs, TimeUnit.MILLISECONDS);
	}

	private synchronized void cancelConnectTimeout() {
		if (connectTimeoutTask != null) {
			connectTimeoutTask.cancel(false);
			connectTimeoutTask = null;
		}
	}

	private synchronized void scheduleReconnect() {
		if (closed || desiredUsername == null || desiredUsername.isBlank()) {
			return;
		}
		if (reconnectTask != null && !reconnectTask.isCancelled()) {
			return;
		}
		long delay = reconnectDelayMs;
		reconnectDelayMs = Math.min(reconnectMaxDelayMs, reconnectDelayMs * 2);
		log("AutoAuction mod socket reconnect scheduled in " + delay + "ms");
		reconnectTask = heartbeatExecutor.schedule(() -> {
			String username;
			String clientVersion;
			synchronized (ModSocketClient.this) {
				reconnectTask = null;
				if (closed || connection != null || connectingUsername != null || desiredUsername == null || desiredUsername.isBlank()) {
					return;
				}
				username = desiredUsername;
				clientVersion = desiredClientVersion;
			}
			connectNow(username, clientVersion);
		}, delay, TimeUnit.MILLISECONDS);
	}

	private synchronized void cancelReconnect() {
		if (reconnectTask != null) {
			reconnectTask.cancel(false);
			reconnectTask = null;
		}
	}

	@Override
	public synchronized void close() {
		closed = true;
		cancelReconnect();
		cancelConnectTimeout();
		closeCurrentConnection();
		heartbeatExecutor.shutdownNow();
	}

	private synchronized void closeCurrentConnection() {
		connectionGeneration++;
		cancelReconnect();
		cancelConnectTimeout();
		stopHeartbeat();
		if (connection != null) {
			if (authenticated) {
				connection.send(GSON.toJson(new StatusMessage("offline")));
				log("AutoAuction mod socket sent offline");
				authenticated = false;
				lastReportedStatus = "offline";
			}
			connection.close();
			log("AutoAuction mod socket closed");
			connection = null;
		}
		connectedUsername = null;
		connectingUsername = null;
	}

	private void log(String message) {
		logSink.accept(message);
	}

	private String cleanImageMime(String imageMime) {
		String clean = String.valueOf(imageMime == null ? "" : imageMime).trim().toLowerCase();
		if (clean.equals("image/png") || clean.equals("image/jpeg")) {
			return clean;
		}
		return "image/jpeg";
	}

	private String cleanLogLevel(String level) {
		String clean = String.valueOf(level == null ? "" : level).trim().toLowerCase();
		return switch (clean) {
			case "debug", "info", "warn", "error" -> clean;
			default -> "info";
		};
	}

	private String cleanLogSource(String source) {
		String clean = String.valueOf(source == null ? "" : source).trim().toLowerCase();
		return switch (clean) {
			case "chat", "system", "debug", "status" -> clean;
			default -> "system";
		};
	}

	private String sanitizeRemoteLogMessage(String messageText) {
		String clean = String.valueOf(messageText == null ? "" : messageText)
			.replaceAll("(?i)(?:\\u00a7|&)[0-9a-fk-or]", "")
			.trim();
		String apiToken = String.valueOf(config.apiToken() == null ? "" : config.apiToken()).trim();
		if (!apiToken.isBlank()) {
			clean = clean.replace(apiToken, "[redacted]");
		}
		return clean;
	}

	private JsonArray cleanLogSegments(List<RemoteLogSegment> segments) {
		JsonArray array = new JsonArray();
		if (segments == null) {
			return array;
		}
		int totalCharacters = 0;
		for (RemoteLogSegment segment : segments) {
			if (segment == null) {
				continue;
			}
			String text = sanitizeRemoteLogSegmentText(segment.text());
			if (text.isEmpty()) {
				continue;
			}
			int remainingCharacters = 1_200 - totalCharacters;
			if (remainingCharacters <= 0) {
				break;
			}
			if (text.length() > remainingCharacters) {
				text = text.substring(0, remainingCharacters);
			}
			totalCharacters += text.length();

			JsonObject object = new JsonObject();
			object.addProperty("text", text);
			if (segment.color() != null && segment.color().matches("^#[0-9A-Fa-f]{6}$")) {
				object.addProperty("color", segment.color().toUpperCase());
			}
			if (segment.bold()) {
				object.addProperty("bold", true);
			}
			if (segment.italic()) {
				object.addProperty("italic", true);
			}
			if (segment.underline()) {
				object.addProperty("underline", true);
			}
			if (segment.strikethrough()) {
				object.addProperty("strikethrough", true);
			}
			array.add(object);
			if (array.size() >= 80) {
				break;
			}
		}
		return array;
	}

	private String sanitizeRemoteLogSegmentText(String messageText) {
		String clean = String.valueOf(messageText == null ? "" : messageText)
			.replaceAll("(?i)(?:\\u00a7|&)[0-9a-fk-or]", "");
		String apiToken = String.valueOf(config.apiToken() == null ? "" : config.apiToken()).trim();
		if (!apiToken.isBlank()) {
			clean = clean.replace(apiToken, "[redacted]");
		}
		return clean;
	}

	static URI socketUri(String apiBaseUrl) {
		String cleanBase = String.valueOf(apiBaseUrl == null ? "" : apiBaseUrl).replaceAll("/+$", "");
		if (cleanBase.startsWith("https://")) {
			return URI.create("wss://" + cleanBase.substring("https://".length()) + "/api/mod/ws");
		}
		if (cleanBase.startsWith("http://")) {
			return URI.create("ws://" + cleanBase.substring("http://".length()) + "/api/mod/ws");
		}
		throw new IllegalArgumentException("apiBaseUrl must start with http:// or https://");
	}

	interface Transport {
		CompletableFuture<Connection> connect(URI uri, Listener listener);
	}

	interface Connection {
		void send(String text);

		void close();
	}

	interface Listener {
		void onOpen(Connection connection);

		void onText(String text);

		void onClose();

		void onError(Throwable error);
	}

	public interface TransferHandler {
		TransferHandler NOOP = new TransferHandler() {
		};

		default void onAccounts(List<TransferAccount> accounts) {
		}

		default void onInvite(TransferSession session) {
		}

		default void onPending(TransferSession session) {
		}

		default void onAccepted(TransferSession session, String role) {
		}

		default void onDeclined(TransferSession session, String reason) {
		}

		default void onCancelled(String sessionId, String reason) {
		}

		default void onRun(TransferRun run) {
		}

		default void onRunSent(TransferRun run) {
		}

		default void onBuyOrderReady(TransferRun run) {
		}

		default void onSellOfferReady(TransferRun run) {
		}

		default void onSellOfferBought(TransferRun run) {
		}

		default void onCycleComplete(TransferCycle cycle) {
		}

		default void onError(String code, String message) {
		}
	}

	public interface ScreenshotHandler {
		ScreenshotHandler NOOP = request -> {
		};

		void onRequest(ScreenshotRequest request);
	}

	public interface RemoteActionHandler {
		RemoteActionHandler NOOP = action -> {
		};

		void onAction(RemoteAction action);
	}

	public interface RegisteredAccountsHandler {
		RegisteredAccountsHandler NOOP = accounts -> {
		};

		void onAccounts(List<RegisteredAccount> accounts);
	}

	public record TransferAccount(String minecraftUsername, String status) {
	}

	public record RegisteredAccount(String minecraftUsername, String status) {
	}

	public record TransferSession(String id, String senderUsername, String receiverUsername, String itemName) {
	}

	public record TransferRun(TransferSession session, int quantity) {
	}

	public record TransferCycle(TransferSession session, int quantity, long before, long after, long delta) {
	}

	public record ScreenshotRequest(int accountId, String requestId) {
	}

	public record RemoteAction(int accountId, String requestId, String actionType, String content) {
	}

	public record RemoteLogSegment(
		String text,
		String color,
		boolean bold,
		boolean italic,
		boolean underline,
		boolean strikethrough
	) {
	}

	private record AuthMessage(String type, String apiKey, String username, String clientVersion) {
		private AuthMessage(String apiKey, String username, String clientVersion) {
			this("auth", apiKey, username, clientVersion);
		}
	}

	private record StatusMessage(String type) {
	}

	private static final class JavaWebSocketTransport implements Transport {
		private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

		@Override
		public CompletableFuture<Connection> connect(URI uri, Listener listener) {
			CompletableFuture<Connection> connectionFuture = new CompletableFuture<>();
			client.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
				@Override
				public void onOpen(WebSocket webSocket) {
					Connection connection = new JavaConnection(webSocket);
					connectionFuture.complete(connection);
					listener.onOpen(connection);
					webSocket.request(1);
				}

				@Override
				public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
					listener.onText(data.toString());
					webSocket.request(1);
					return CompletableFuture.completedFuture(null);
				}

				@Override
				public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
					listener.onClose();
					return CompletableFuture.completedFuture(null);
				}

				@Override
				public void onError(WebSocket webSocket, Throwable error) {
					connectionFuture.completeExceptionally(error);
					listener.onError(error);
				}
			}).whenComplete((webSocket, error) -> {
				if (error != null) {
					connectionFuture.completeExceptionally(error);
				}
			});
			return connectionFuture;
		}
	}

	private record JavaConnection(WebSocket webSocket) implements Connection {
		@Override
		public void send(String text) {
			webSocket.sendText(text, true);
		}

		@Override
		public void close() {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing");
		}
	}
}
