package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferControllerTest {
	@Test
	void formatsConnectedAccountList() {
		TransferController controller = new TransferController();

		List<String> lines = controller.showAccounts(List.of(
			new TransferController.ConnectedAccount("ReceiverOne", "hypixel"),
			new TransferController.ConnectedAccount("ReceiverTwo", "active")
		));

		assertEquals(List.of(
			"AutoAuction transfer connected accounts:",
			"- ReceiverOne (hypixel)",
			"- ReceiverTwo (active)"
		), lines);
	}

	@Test
	void formatsEmptyConnectedAccountList() {
		TransferController controller = new TransferController();

		assertEquals(List.of("AutoAuction transfer: no connected accounts."), controller.showAccounts(List.of()));
	}

	@Test
	void cachesConnectedAccountUsernamesForCommandSuggestions() {
		TransferController controller = new TransferController();

		controller.showAccounts(List.of(
			new TransferController.ConnectedAccount("ReceiverOne", "hypixel"),
			new TransferController.ConnectedAccount("ReceiverTwo", "active")
		));

		assertEquals(List.of("ReceiverOne", "ReceiverTwo"), controller.connectedAccountUsernames());
		assertEquals(List.of("ReceiverOne", "ReceiverTwo"), controller.pendingSenderUsernames());
	}

	@Test
	void tracksIncomingInviteUntilAccepted() {
		TransferController controller = new TransferController();

		String invite = controller.incomingInvite(new TransferController.Session(
			"s1",
			"SenderPlayer",
			"ReceiverPlayer",
			"ENCHANTED DIAMOND"
		));

		assertEquals("AutoAuction transfer invite from SenderPlayer for ENCHANTED DIAMOND. Run /mf accept SenderPlayer or /mf decline SenderPlayer.", invite);
		assertEquals(TransferController.State.INCOMING_INVITE, controller.state());
		assertTrue(controller.canAcceptFrom("SenderPlayer"));
		assertFalse(controller.canAcceptFrom("OtherPlayer"));
		assertEquals(List.of("SenderPlayer"), controller.pendingSenderUsernames());
	}

	@Test
	void tracksOutgoingPendingAndAcceptedSenderSession() {
		TransferController controller = new TransferController();

		String pending = controller.outgoingPending(new TransferController.Session(
			"s1",
			"SenderPlayer",
			"ReceiverPlayer",
			"ENCHANTED DIAMOND"
		));
		String accepted = controller.accepted(new TransferController.Session(
			"s1",
			"SenderPlayer",
			"ReceiverPlayer",
			"ENCHANTED DIAMOND"
		), TransferController.Role.SENDER);

		assertEquals("AutoAuction transfer invite sent to ReceiverPlayer for ENCHANTED DIAMOND.", pending);
		assertEquals(
			"AutoAuction transfer paired. You are sender. Sender: SenderPlayer. Receiver: ReceiverPlayer. Item: ENCHANTED DIAMOND. Sender holds the transfer items and coins. Receiver starts clear of this item and does not hold the transfer coins. Sender runs /mf run <target> when both accounts are ready.",
			accepted
		);
		assertEquals(TransferController.State.PAIRED, controller.state());
		assertEquals(TransferController.Role.SENDER, controller.role());
		assertTrue(controller.canRunAsSender());
		assertEquals("ENCHANTED DIAMOND", controller.session().itemName());
	}

	@Test
	void formatsRunMessagesForSenderAndReceiver() {
		TransferController sender = new TransferController();
		TransferController receiver = new TransferController();
		TransferController.Session session = new TransferController.Session(
			"s1",
			"SenderPlayer",
			"ReceiverPlayer",
			"ENCHANTED DIAMOND"
		);
		sender.accepted(session, TransferController.Role.SENDER);
		receiver.accepted(session, TransferController.Role.RECEIVER);

		assertEquals("AutoAuction transfer run sent to ReceiverPlayer: 128x ENCHANTED DIAMOND.", sender.runSent(128));
		assertEquals("AutoAuction transfer received: creating buy order for 128x ENCHANTED DIAMOND.", receiver.incomingRun(128));
		assertFalse(receiver.canRunAsSender());
	}

	@Test
	void declineCancelAndErrorResetState() {
		TransferController controller = new TransferController();
		controller.incomingInvite(new TransferController.Session("s1", "SenderPlayer", "ReceiverPlayer", "ENCHANTED DIAMOND"));

		assertEquals("AutoAuction transfer declined: not ready", controller.declined("not ready"));
		assertEquals(TransferController.State.IDLE, controller.state());

		controller.outgoingPending(new TransferController.Session("s2", "SenderPlayer", "ReceiverPlayer", "ENCHANTED DIAMOND"));
		assertEquals("AutoAuction transfer cancelled: sender cancelled", controller.cancelled("sender cancelled"));
		assertEquals(TransferController.State.IDLE, controller.state());

		controller.outgoingPending(new TransferController.Session("s3", "SenderPlayer", "ReceiverPlayer", "ENCHANTED DIAMOND"));
		assertEquals("AutoAuction transfer error target_offline: ReceiverPlayer is not connected", controller.error("target_offline", "ReceiverPlayer is not connected"));
		assertEquals(TransferController.State.IDLE, controller.state());
	}
}
