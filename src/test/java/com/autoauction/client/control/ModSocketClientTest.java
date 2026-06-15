package com.autoauction.client.control;

import com.autoauction.client.config.AutoAuctionConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSocketClientTest {
	@Test
	void connectsToModWebSocketAndSendsAuthPayload() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = new AutoAuctionConfig("https://lazy-similarly-reaffirm.ngrok-free.dev/", "hpx_test_mod",
			"", "", "/stopmacro", "/hub", false, true, true, List.of("localhost"), 25_000, 1_000_000,
			30_000_000, 8_000, 250, 5_000);
		ModSocketClient client = new ModSocketClient(config, transport, 1);

		client.start("SocketPlayer", "26.1.1");
		transport.open();

		assertEquals(URI.create("wss://lazy-similarly-reaffirm.ngrok-free.dev/api/mod/ws"), transport.connectedUri);
		assertTrue(transport.connection.sentMessages.getFirst().contains("\"type\":\"auth\""));
		assertTrue(transport.connection.sentMessages.getFirst().contains("\"apiKey\":\"hpx_test_mod\""));
		assertTrue(transport.connection.sentMessages.getFirst().contains("\"username\":\"SocketPlayer\""));
		assertTrue(transport.connection.sentMessages.getFirst().contains("\"clientVersion\":\"26.1.1\""));

		client.close();
	}

	@Test
	void sendsHeartbeatAfterAuthOk() throws Exception {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = new AutoAuctionConfig("http://127.0.0.1:3000", "hpx_test_mod",
			"", "", "/stopmacro", "/hub", false, true, true, List.of("localhost"), 25_000, 1_000_000,
			30_000_000, 8_000, 250, 5_000);
		ModSocketClient client = new ModSocketClient(config, transport, 1);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");

		assertEquals(URI.create("ws://127.0.0.1:3000/api/mod/ws"), transport.connectedUri);
		assertTrue(transport.connection.awaitMessage("\"type\":\"heartbeat\""));

		client.close();
	}

	@Test
	void doesNotConnectWithoutApiToken() {
		FakeTransport transport = new FakeTransport();
		ModSocketClient client = new ModSocketClient(AutoAuctionConfig.defaults(), transport, 1);

		client.start("SocketPlayer", "26.1.1");

		assertEquals(0, transport.connectCalls);
		client.close();
	}

	private static final class FakeTransport implements ModSocketClient.Transport {
		private final FakeConnection connection = new FakeConnection();
		private ModSocketClient.Listener listener;
		private URI connectedUri;
		private int connectCalls;

		@Override
		public CompletableFuture<ModSocketClient.Connection> connect(URI uri, ModSocketClient.Listener listener) {
			this.connectedUri = uri;
			this.listener = listener;
			this.connectCalls++;
			return CompletableFuture.completedFuture(connection);
		}

		void open() {
			listener.onOpen(connection);
		}

		void message(String text) {
			listener.onText(text);
		}
	}

	private static final class FakeConnection implements ModSocketClient.Connection {
		private final List<String> sentMessages = new ArrayList<>();
		private boolean closed;

		@Override
		public synchronized void send(String text) {
			sentMessages.add(text);
			notifyAll();
		}

		@Override
		public void close() {
			closed = true;
		}

		synchronized boolean awaitMessage(String needle) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
			while (System.nanoTime() < deadline) {
				if (sentMessages.stream().anyMatch(message -> message.contains(needle))) {
					return true;
				}
				TimeUnit.MILLISECONDS.timedWait(this, 10);
			}
			return sentMessages.stream().anyMatch(message -> message.contains(needle));
		}
	}
}
