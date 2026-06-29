package com.autoauction.client.lobby;

import com.autoauction.client.debug.SkyBlockStatus;
import com.autoauction.client.macro.NebulaMacroController;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LobbyCollisionController {
	private static final Pattern USERNAME_TOKEN = Pattern.compile("\\b[A-Za-z0-9_]{3,16}\\b");
	private static final long PLAYER_STABLE_WAIT_MS = 1_000L;
	private static final String ISLAND_COMMAND = "/is";

	private final Set<String> registeredUsernames = new HashSet<>();
	private State state = State.IDLE;
	private boolean previousAreaKnown;
	private boolean wasInTheEnd;
	private boolean scanWhenInEnd;
	private long playerAvailableSince = -1L;

	public void updateRegisteredAccounts(Collection<String> usernames) {
		registeredUsernames.clear();
		if (usernames == null) {
			return;
		}
		for (String username : usernames) {
			String clean = normalizeUsername(username);
			if (!clean.isBlank()) {
				registeredUsernames.add(clean);
			}
		}
	}

	public void tick(Snapshot snapshot, NebulaMacroController macroController, Consumer<String> commandSink) {
		if (snapshot == null || macroController == null || commandSink == null) {
			return;
		}

		boolean inTheEnd = isInTheEnd(snapshot.status());
		if (!previousAreaKnown) {
			previousAreaKnown = true;
			wasInTheEnd = inTheEnd;
		} else if (!wasInTheEnd && inTheEnd) {
			scanWhenInEnd = true;
			wasInTheEnd = true;
		} else if (wasInTheEnd && !inTheEnd) {
			wasInTheEnd = false;
		}

		switch (state) {
			case IDLE -> maybeStartSwitch(snapshot, macroController, commandSink, inTheEnd);
			case ENSURE_OFF -> tickEnsureOff(snapshot, macroController, commandSink);
			case WAIT_PLAYER_STABLE -> tickWaitPlayerStable(snapshot, macroController, commandSink);
			case ENSURE_ON -> tickEnsureOn(snapshot, macroController, commandSink, inTheEnd);
		}
	}

	private void maybeStartSwitch(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		boolean inTheEnd
	) {
		if (!scanWhenInEnd || !inTheEnd || !macroController.desiredOn()
			|| macroController.observedState() != NebulaMacroController.ObservedState.ON) {
			return;
		}
		Optional<String> collision = visibleRegisteredAccount(snapshot.currentUsername(), snapshot.tabListLines());
		scanWhenInEnd = false;
		if (collision.isEmpty()) {
			return;
		}
		state = State.ENSURE_OFF;
		tickEnsureOff(snapshot, macroController, commandSink);
	}

	private void tickEnsureOff(Snapshot snapshot, NebulaMacroController macroController, Consumer<String> commandSink) {
		NebulaMacroController.EnsureResult result = macroController.ensureOff(commandSink, snapshot.nowMs());
		if (result != NebulaMacroController.EnsureResult.COMPLETE) {
			return;
		}
		commandSink.accept(ISLAND_COMMAND);
		state = State.WAIT_PLAYER_STABLE;
		playerAvailableSince = snapshot.playerAvailable() ? snapshot.nowMs() : -1L;
	}

	private void tickWaitPlayerStable(Snapshot snapshot, NebulaMacroController macroController, Consumer<String> commandSink) {
		if (!snapshot.playerAvailable()) {
			playerAvailableSince = -1L;
			return;
		}
		if (playerAvailableSince < 0L) {
			playerAvailableSince = snapshot.nowMs();
			return;
		}
		if (snapshot.nowMs() - playerAvailableSince < PLAYER_STABLE_WAIT_MS) {
			return;
		}
		state = State.ENSURE_ON;
		tickEnsureOn(snapshot, macroController, commandSink, isInTheEnd(snapshot.status()));
	}

	private void tickEnsureOn(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		boolean inTheEnd
	) {
		NebulaMacroController.EnsureResult result = macroController.ensureOn(commandSink, snapshot.nowMs());
		if (result != NebulaMacroController.EnsureResult.COMPLETE) {
			return;
		}
		state = State.IDLE;
		playerAvailableSince = -1L;
		scanWhenInEnd = true;
		maybeStartSwitch(snapshot, macroController, commandSink, inTheEnd);
	}

	private Optional<String> visibleRegisteredAccount(String currentUsername, List<String> tabListLines) {
		String current = normalizeUsername(currentUsername);
		for (String line : tabListLines == null ? List.<String>of() : tabListLines) {
			Matcher matcher = USERNAME_TOKEN.matcher(SkyBlockStatus.cleanLine(line));
			while (matcher.find()) {
				String candidate = normalizeUsername(matcher.group());
				if (!candidate.equals(current) && registeredUsernames.contains(candidate)) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	private static boolean isInTheEnd(SkyBlockStatus status) {
		return status != null && status.area()
			.map(area -> SkyBlockStatus.cleanLine(area).equalsIgnoreCase("The End"))
			.orElse(false);
	}

	private static String normalizeUsername(String username) {
		return String.valueOf(username == null ? "" : username).trim().toLowerCase(Locale.ROOT);
	}

	public record Snapshot(
		String currentUsername,
		SkyBlockStatus status,
		List<String> tabListLines,
		boolean playerAvailable,
		long nowMs
	) {
	}

	private enum State {
		IDLE,
		ENSURE_OFF,
		WAIT_PLAYER_STABLE,
		ENSURE_ON
	}
}
