package com.autoauction.client.control;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ModAccountStatusDetector;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSocketClientTest {
	@Test
	void connectsToModWebSocketAndSendsAuthPayload() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("https://lazy-similarly-reaffirm.ngrok-free.dev/", "hpx_test_mod");
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
	void sendsActiveAfterAuthOkThenHeartbeats() throws Exception {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 1);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");

		assertEquals(URI.create("ws://127.0.0.1:3000/api/mod/ws"), transport.connectedUri);
		assertTrue(transport.connection.sentMessages.get(1).contains("\"type\":\"active\""));
		assertTrue(transport.connection.awaitMessage("\"type\":\"heartbeat\""));

		client.close();
	}

	@Test
	void sendsOfflineBeforeClosingAuthenticatedConnection() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		client.close();

		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"offline\"")));
		assertTrue(transport.connection.closed);
	}

	@Test
	void reconnectsWhenUsernameChanges() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.ensureStartedFor("FirstPlayer", "1.0.0");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		FakeConnection firstConnection = transport.connection;

		client.ensureStartedFor("SecondPlayer", "1.0.0");
		transport.open();

		assertEquals(2, transport.connectCalls);
		assertTrue(firstConnection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"offline\"")));
		assertTrue(firstConnection.closed);
		assertTrue(transport.connection.sentMessages.getFirst().contains("\"username\":\"SecondPlayer\""));

		client.close();
	}

	@Test
	void doesNotStartDuplicateSocketWhileConnectionIsPending() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.ensureStartedFor("SocketPlayer", "1.0.0");
		client.ensureStartedFor("SocketPlayer", "1.0.0");
		client.ensureStartedFor("SocketPlayer", "1.0.0");

		assertEquals(1, transport.connectCalls);
		client.close();
	}

	@Test
	void ignoresLateCloseFromPreviousSocketAfterUsernameChanges() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.ensureStartedFor("FirstPlayer", "1.0.0");
		transport.open();
		client.ensureStartedFor("SecondPlayer", "1.0.0");
		transport.closeConnection(0);
		client.ensureStartedFor("SecondPlayer", "1.0.0");

		assertEquals(2, transport.connectCalls);
		client.close();
	}

	@Test
	void sendsExplicitStatusUpdatesAfterAuthOk() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		client.reportStatus("hypixel");
		client.reportStatus("banned");

		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"hypixel\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"banned\"")));
		client.close();
	}

	@Test
	void sendsBanMetadataWithBannedStatus() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		client.reportBan(new ModAccountStatusDetector.BanDetails(
			true,
			"Cheating through the use of unfair game advantages.",
			"#01346337",
			2526362000L,
			"2026-07-14T20:46:02Z"
		));

		String payload = transport.connection.sentMessages.stream()
			.filter(message -> message.contains("\"type\":\"banned\""))
			.findFirst()
			.orElseThrow();
		assertTrue(payload.contains("\"banReason\":\"Cheating through the use of unfair game advantages.\""));
		assertTrue(payload.contains("\"banId\":\"#01346337\""));
		assertTrue(payload.contains("\"banUntil\":\"2026-07-14T20:46:02Z\""));
		assertTrue(payload.contains("\"banDurationMs\":2526362000"));
		client.close();
	}

	@Test
	void invokesDisconnectHandlerWhenServerSendsDisconnectNow() {
		FakeTransport transport = new FakeTransport();
		List<String> disconnectReasons = new ArrayList<>();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000, message -> {}, disconnectReasons::add);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		transport.message("{\"type\":\"disconnect_now\",\"reason\":\"Ban detected on BannedPlayer\"}");

		assertEquals(List.of("Ban detected on BannedPlayer"), disconnectReasons);
		client.close();
	}

	@Test
	void logsWebSocketLifecycleWithoutLeakingApiKey() throws Exception {
		FakeTransport transport = new FakeTransport();
		List<String> logs = new ArrayList<>();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_secret_mod_key");
		ModSocketClient client = new ModSocketClient(config, transport, 1, logs::add);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		assertTrue(transport.connection.awaitMessage("\"type\":\"heartbeat\""));
		client.close();

		assertTrue(logs.stream().anyMatch(log -> log.contains("connecting")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("opened")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("sent auth")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("received auth_ok")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("sent active")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("heartbeat started")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("sent heartbeat")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("sent offline")));
		assertTrue(logs.stream().anyMatch(log -> log.contains("closed")));
		assertFalse(String.join("\n", logs).contains("hpx_test_secret_mod_key"));
	}

	@Test
	void logsWebSocketErrors() {
		FakeTransport transport = new FakeTransport();
		List<String> logs = new ArrayList<>();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000, logs::add);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.error(new IllegalStateException("server down"));

		assertTrue(logs.stream().anyMatch(log -> log.contains("error") && log.contains("server down")));
		client.close();
	}

	@Test
	void reconnectsAfterRemoteCloseAndResendsLastStatus() throws Exception {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		client.reportStatus("hypixel");
		transport.closeConnection(0);

		assertTrue(transport.awaitConnectCalls(2));
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");

		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"hypixel\"")));
		assertFalse(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"active\"")));
		client.close();
	}

	@Test
	void reconnectsAfterSocketError() throws Exception {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SocketPlayer", "26.1.1");
		transport.open();
		transport.error(new IllegalStateException("wifi changed"));

		assertTrue(transport.awaitConnectCalls(2));
		client.close();
	}

	@Test
	void tickEnsureDoesNotBypassScheduledReconnectBackoff() throws Exception {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.ensureStartedFor("SocketPlayer", "26.1.1");
		transport.open();
		transport.error(new IllegalStateException("wifi changed"));
		client.ensureStartedFor("SocketPlayer", "26.1.1");
		client.ensureStartedFor("SocketPlayer", "26.1.1");

		assertEquals(1, transport.connectCalls);
		assertTrue(transport.awaitConnectCalls(2));
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

	@Test
	void sendsTransferCommandPayloadsAfterAuthOk() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SenderPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		client.requestTransferAccounts();
		client.inviteTransfer("ReceiverPlayer", "ENCHANTED DIAMOND");
		client.acceptTransfer("SenderPlayer");
		client.declineTransfer("SenderPlayer");
		client.cancelTransfer();
		client.runTransfer(128);
		client.buyOrderReady(128);
		client.sellOfferReady(128);
		client.sellOfferBought(128);
		client.cycleComplete(128, 1_000_000L, 18_500_000L, 17_500_000L);

		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_list\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_invite\"")
			&& message.contains("\"receiverUsername\":\"ReceiverPlayer\"")
			&& message.contains("\"itemName\":\"ENCHANTED DIAMOND\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_accept\"")
			&& message.contains("\"senderUsername\":\"SenderPlayer\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_decline\"")
			&& message.contains("\"senderUsername\":\"SenderPlayer\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_cancel\"")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_run\"")
			&& message.contains("\"quantity\":128")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_buy_order_ready\"")
			&& message.contains("\"quantity\":128")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_sell_offer_ready\"")
			&& message.contains("\"quantity\":128")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_sell_offer_bought\"")
			&& message.contains("\"quantity\":128")));
		assertTrue(transport.connection.sentMessages.stream().anyMatch(message -> message.contains("\"type\":\"transfer_cycle_complete\"")
			&& message.contains("\"quantity\":128")
			&& message.contains("\"before\":1000000")
			&& message.contains("\"after\":18500000")
			&& message.contains("\"delta\":17500000")));
		client.close();
	}

	@Test
	void ignoresTransferCommandPayloadsBeforeAuthOk() {
		FakeTransport transport = new FakeTransport();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000);

		client.start("SenderPlayer", "26.1.1");
		transport.open();

		assertFalse(client.requestTransferAccounts());
		assertFalse(client.inviteTransfer("ReceiverPlayer", "ENCHANTED DIAMOND"));
		assertFalse(client.acceptTransfer("SenderPlayer"));
		assertFalse(client.declineTransfer("SenderPlayer"));
		assertFalse(client.cancelTransfer());
		assertFalse(client.runTransfer(128));
		assertFalse(client.buyOrderReady(128));
		assertFalse(client.sellOfferReady(128));
		assertFalse(client.sellOfferBought(128));
		assertFalse(client.cycleComplete(128, 1_000_000L, 18_500_000L, 17_500_000L));
		assertEquals(1, transport.connection.sentMessages.size());
		client.close();
	}

	@Test
	void invokesTransferHandlerForIncomingServerMessages() {
		FakeTransport transport = new FakeTransport();
		List<String> events = new ArrayList<>();
		AutoAuctionConfig config = config("http://127.0.0.1:3000", "hpx_test_mod");
		ModSocketClient client = new ModSocketClient(config, transport, 30_000, message -> {}, reason -> {}, new ModSocketClient.TransferHandler() {
			@Override
			public void onAccounts(List<ModSocketClient.TransferAccount> accounts) {
				events.add("accounts:" + accounts.size() + ":" + accounts.getFirst().minecraftUsername());
			}

			@Override
			public void onInvite(ModSocketClient.TransferSession session) {
				events.add("invite:" + session.senderUsername() + ":" + session.itemName());
			}

			@Override
			public void onPending(ModSocketClient.TransferSession session) {
				events.add("pending:" + session.receiverUsername());
			}

			@Override
			public void onAccepted(ModSocketClient.TransferSession session, String role) {
				events.add("accepted:" + role + ":" + session.id());
			}

			@Override
			public void onDeclined(ModSocketClient.TransferSession session, String reason) {
				events.add("declined:" + reason);
			}

			@Override
			public void onCancelled(String sessionId, String reason) {
				events.add("cancelled:" + sessionId + ":" + reason);
			}

			@Override
			public void onRun(ModSocketClient.TransferRun run) {
				events.add("run:" + run.session().itemName() + ":" + run.quantity());
			}

			@Override
			public void onRunSent(ModSocketClient.TransferRun run) {
				events.add("run-sent:" + run.session().receiverUsername() + ":" + run.quantity());
			}

			@Override
			public void onBuyOrderReady(ModSocketClient.TransferRun run) {
				events.add("buy-order-ready:" + run.session().itemName() + ":" + run.quantity());
			}

			@Override
			public void onSellOfferReady(ModSocketClient.TransferRun run) {
				events.add("sell-offer-ready:" + run.session().itemName() + ":" + run.quantity());
			}

			@Override
			public void onSellOfferBought(ModSocketClient.TransferRun run) {
				events.add("sell-offer-bought:" + run.session().itemName() + ":" + run.quantity());
			}

			@Override
			public void onCycleComplete(ModSocketClient.TransferCycle cycle) {
				events.add("cycle-complete:" + cycle.session().itemName() + ":" + cycle.quantity() + ":" + cycle.before() + ":" + cycle.after() + ":" + cycle.delta());
			}

			@Override
			public void onError(String code, String message) {
				events.add("error:" + code + ":" + message);
			}
		});

		client.start("SenderPlayer", "26.1.1");
		transport.open();
		transport.message("{\"type\":\"auth_ok\"}");
		transport.message("{\"type\":\"transfer_accounts\",\"accounts\":[{\"minecraftUsername\":\"ReceiverPlayer\",\"status\":\"hypixel\"}]}");
		transport.message("{\"type\":\"transfer_invite\",\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_pending\",\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_accepted\",\"role\":\"sender\",\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_declined\",\"reason\":\"not ready\",\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_cancelled\",\"sessionId\":\"s1\",\"reason\":\"sender cancelled\"}");
		transport.message("{\"type\":\"transfer_run\",\"quantity\":128,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_run_sent\",\"quantity\":128,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_buy_order_ready\",\"quantity\":128,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_sell_offer_ready\",\"quantity\":128,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_sell_offer_bought\",\"quantity\":128,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_cycle_complete\",\"quantity\":128,\"before\":1000000,\"after\":18500000,\"delta\":17500000,\"session\":{\"id\":\"s1\",\"senderUsername\":\"SenderPlayer\",\"receiverUsername\":\"ReceiverPlayer\",\"itemName\":\"ENCHANTED DIAMOND\"}}");
		transport.message("{\"type\":\"transfer_error\",\"code\":\"target_offline\",\"message\":\"ReceiverPlayer is not connected\"}");

		assertEquals(List.of(
			"accounts:1:ReceiverPlayer",
			"invite:SenderPlayer:ENCHANTED DIAMOND",
			"pending:ReceiverPlayer",
			"accepted:sender:s1",
			"declined:not ready",
			"cancelled:s1:sender cancelled",
			"run:ENCHANTED DIAMOND:128",
			"run-sent:ReceiverPlayer:128",
			"buy-order-ready:ENCHANTED DIAMOND:128",
			"sell-offer-ready:ENCHANTED DIAMOND:128",
			"sell-offer-bought:ENCHANTED DIAMOND:128",
			"cycle-complete:ENCHANTED DIAMOND:128:1000000:18500000:17500000",
			"error:target_offline:ReceiverPlayer is not connected"
		), events);
		client.close();
	}

	private AutoAuctionConfig config(String baseUrl, String apiKey) {
		return new AutoAuctionConfig(baseUrl, apiKey, "", "", "/stopmacro", "", "/hub", true, 25_000, 8_000, 250, 5_000);
	}

	private static final class FakeTransport implements ModSocketClient.Transport {
		private FakeConnection connection = new FakeConnection();
		private ModSocketClient.Listener listener;
		private final List<ModSocketClient.Listener> listeners = new ArrayList<>();
		private URI connectedUri;
		private int connectCalls;

		@Override
		public CompletableFuture<ModSocketClient.Connection> connect(URI uri, ModSocketClient.Listener listener) {
			this.connection = new FakeConnection();
			this.connectedUri = uri;
			this.listener = listener;
			this.listeners.add(listener);
			this.connectCalls++;
			synchronized (this) {
				notifyAll();
			}
			return CompletableFuture.completedFuture(connection);
		}

		void open() {
			listener.onOpen(connection);
		}

		void message(String text) {
			listener.onText(text);
		}

		void error(Throwable error) {
			listener.onError(error);
		}

		void closeConnection(int index) {
			listeners.get(index).onClose();
		}

		synchronized boolean awaitConnectCalls(int expected) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
			while (System.nanoTime() < deadline) {
				if (connectCalls >= expected) {
					return true;
				}
				TimeUnit.MILLISECONDS.timedWait(this, 10);
			}
			return connectCalls >= expected;
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
