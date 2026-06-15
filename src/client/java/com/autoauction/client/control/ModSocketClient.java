package com.autoauction.client.control;

import com.autoauction.client.config.AutoAuctionConfig;
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

public final class ModSocketClient implements AutoCloseable {
	private static final Gson GSON = new Gson();
	private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000;

	private final AutoAuctionConfig config;
	private final Transport transport;
	private final long heartbeatIntervalMs;
	private final ScheduledExecutorService heartbeatExecutor;

	private Connection connection;
	private ScheduledFuture<?> heartbeatTask;

	public ModSocketClient(AutoAuctionConfig config) {
		this(config, new JavaWebSocketTransport(), DEFAULT_HEARTBEAT_INTERVAL_MS);
	}

	ModSocketClient(AutoAuctionConfig config, Transport transport, long heartbeatIntervalMs) {
		this.config = config;
		this.transport = transport;
		this.heartbeatIntervalMs = heartbeatIntervalMs;
		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "autoauction-mod-socket-heartbeat");
			thread.setDaemon(true);
			return thread;
		});
	}

	public synchronized boolean start(String username, String clientVersion) {
		if (connection != null || config.apiToken().isBlank() || String.valueOf(username).isBlank()) {
			return false;
		}

		transport.connect(socketUri(config.apiBaseUrl()), new Listener() {
			@Override
			public void onOpen(Connection openedConnection) {
				synchronized (ModSocketClient.this) {
					connection = openedConnection;
				}
				openedConnection.send(GSON.toJson(new AuthMessage(config.apiToken(), username, clientVersion)));
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
				}
			}

			@Override
			public void onError(Throwable error) {
				stopHeartbeat();
				synchronized (ModSocketClient.this) {
					connection = null;
				}
			}
		});
		return true;
	}

	private void handleMessage(String text) {
		JsonObject message = GSON.fromJson(text, JsonObject.class);
		if (message == null || !message.has("type")) {
			return;
		}
		if (Objects.equals(message.get("type").getAsString(), "auth_ok")) {
			startHeartbeat();
		}
	}

	private synchronized void startHeartbeat() {
		if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
			return;
		}
		heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
			Connection current;
			synchronized (ModSocketClient.this) {
				current = connection;
			}
			if (current != null) {
				current.send("{\"type\":\"heartbeat\"}");
			}
		}, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
	}

	private synchronized void stopHeartbeat() {
		if (heartbeatTask != null) {
			heartbeatTask.cancel(false);
			heartbeatTask = null;
		}
	}

	@Override
	public synchronized void close() {
		stopHeartbeat();
		if (connection != null) {
			connection.close();
			connection = null;
		}
		heartbeatExecutor.shutdownNow();
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
