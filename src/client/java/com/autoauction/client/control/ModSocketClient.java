package com.autoauction.client.control;

import com.autoauction.Autoauction;
import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ModAccountStatusDetector;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
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

	private final AutoAuctionConfig config;
	private final Transport transport;
	private final long heartbeatIntervalMs;
	private final ScheduledExecutorService heartbeatExecutor;
	private final Consumer<String> logSink;

	private Connection connection;
	private ScheduledFuture<?> heartbeatTask;
	private boolean authenticated;
	private String lastReportedStatus;

	public ModSocketClient(AutoAuctionConfig config) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS, message -> Autoauction.LOGGER.info(message));
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs) {
		this(config, transport, heartbeatIntervalMs, message -> Autoauction.LOGGER.info(message));
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs, Consumer<String> logSink) {
		this.config = config;
		this.transport = transport;
		this.heartbeatIntervalMs = heartbeatIntervalMs;
		this.logSink = logSink;
		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "autoauction-mod-socket-heartbeat");
			thread.setDaemon(true);
			return thread;
		});
	}

	public synchronized boolean start(String username, String clientVersion) {
		if (connection != null || config.apiToken().isBlank() || String.valueOf(username).isBlank()) {
			log("AutoAuction mod socket not started: connection exists, API token missing, or username missing");
			return false;
		}

		URI uri = socketUri(config.apiBaseUrl());
		log("AutoAuction mod socket connecting to " + uri);
		transport.connect(uri, new Listener() {
			@Override
			public void onOpen(Connection openedConnection) {
				synchronized (ModSocketClient.this) {
					connection = openedConnection;
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
				synchronized (ModSocketClient.this) {
					connection = null;
					authenticated = false;
					lastReportedStatus = null;
				}
				log("AutoAuction mod socket closed by remote");
			}

			@Override
			public void onError(Throwable error) {
				stopHeartbeat();
				synchronized (ModSocketClient.this) {
					connection = null;
					authenticated = false;
					lastReportedStatus = null;
				}
				log("AutoAuction mod socket error: " + error.getMessage());
			}
		});
		return true;
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
			sendStatus("active");
			startHeartbeat();
		}
	}

	public synchronized boolean reportStatus(String status) {
		if (!authenticated || Objects.equals(lastReportedStatus, status)) {
			return false;
		}
		return sendStatus(status);
	}

	public synchronized boolean reportBan(ModAccountStatusDetector.BanDetails details) {
		if (!authenticated || Objects.equals(lastReportedStatus, "banned")) {
			return false;
		}
		return sendBan(details);
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

	@Override
	public synchronized void close() {
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
		heartbeatExecutor.shutdownNow();
	}

	private void log(String message) {
		logSink.accept(message);
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
