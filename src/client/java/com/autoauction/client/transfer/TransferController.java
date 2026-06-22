package com.autoauction.client.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransferController {
	private State state = State.IDLE;
	private Session session;
	private Role role;
	private List<ConnectedAccount> connectedAccounts = List.of();

	public List<String> showAccounts(List<ConnectedAccount> accounts) {
		connectedAccounts = accounts == null ? List.of() : List.copyOf(accounts);
		if (accounts == null || accounts.isEmpty()) {
			return List.of("AutoAuction transfer: no connected accounts.");
		}

		List<String> lines = new ArrayList<>();
		lines.add("AutoAuction transfer connected accounts:");
		for (ConnectedAccount account : accounts) {
			lines.add("- " + account.minecraftUsername() + " (" + account.status() + ")");
		}
		return lines;
	}

	public String incomingInvite(Session nextSession) {
		this.session = nextSession;
		this.role = Role.RECEIVER;
		this.state = State.INCOMING_INVITE;
		return "AutoAuction transfer invite from " + nextSession.senderUsername() + " for " + nextSession.itemName()
			+ ". Run /autoauction transfer accept " + nextSession.senderUsername()
			+ " or /autoauction transfer decline " + nextSession.senderUsername() + ".";
	}

	public String outgoingPending(Session nextSession) {
		this.session = nextSession;
		this.role = Role.SENDER;
		this.state = State.OUTGOING_INVITE;
		return "AutoAuction transfer invite sent to " + nextSession.receiverUsername() + " for " + nextSession.itemName() + ".";
	}

	public String accepted(Session nextSession, Role nextRole) {
		this.session = nextSession;
		this.role = nextRole;
		this.state = State.PAIRED;
		String partner = nextRole == Role.SENDER ? nextSession.receiverUsername() : nextSession.senderUsername();
		return "AutoAuction transfer paired as " + nextRole.name().toLowerCase(Locale.ROOT) + " with " + partner
			+ " for " + nextSession.itemName() + ". Bazaar automation is waiting for menu dumps.";
	}

	public String declined(String reason) {
		clear();
		return "AutoAuction transfer declined: " + cleanReason(reason, "declined");
	}

	public String cancelled(String reason) {
		clear();
		return "AutoAuction transfer cancelled: " + cleanReason(reason, "cancelled");
	}

	public String error(String code, String message) {
		clear();
		return "AutoAuction transfer error " + cleanReason(code, "unknown") + ": " + cleanReason(message, "Transfer failed");
	}

	public boolean canAcceptFrom(String senderUsername) {
		return state == State.INCOMING_INVITE
			&& session != null
			&& session.senderUsername().equalsIgnoreCase(String.valueOf(senderUsername == null ? "" : senderUsername).trim());
	}

	public boolean canRunAsSender() {
		return state == State.PAIRED && role == Role.SENDER && session != null;
	}

	public String runSent(int quantity) {
		if (session == null) {
			return "AutoAuction transfer run sent.";
		}
		return "AutoAuction transfer run sent to " + session.receiverUsername() + ": "
			+ Math.max(1, quantity) + "x " + session.itemName() + ".";
	}

	public String incomingRun(int quantity) {
		if (session == null) {
			return "AutoAuction transfer received.";
		}
		return "AutoAuction transfer received: creating buy order for "
			+ Math.max(1, quantity) + "x " + session.itemName() + ".";
	}

	public State state() {
		return state;
	}

	public Role role() {
		return role;
	}

	public Session session() {
		return session;
	}

	public List<String> connectedAccountUsernames() {
		return connectedAccounts.stream()
			.map(ConnectedAccount::minecraftUsername)
			.toList();
	}

	public List<String> pendingSenderUsernames() {
		if (state == State.INCOMING_INVITE && session != null) {
			return List.of(session.senderUsername());
		}
		return connectedAccountUsernames();
	}

	private void clear() {
		this.state = State.IDLE;
		this.session = null;
		this.role = null;
	}

	private static String cleanReason(String value, String fallback) {
		String clean = String.valueOf(value == null ? "" : value).trim();
		return clean.isBlank() ? fallback : clean;
	}

	public enum State {
		IDLE,
		OUTGOING_INVITE,
		INCOMING_INVITE,
		PAIRED
	}

	public enum Role {
		SENDER,
		RECEIVER
	}

	public record ConnectedAccount(String minecraftUsername, String status) {
	}

	public record Session(String id, String senderUsername, String receiverUsername, String itemName) {
	}
}
