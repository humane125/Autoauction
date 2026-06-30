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
	private static final long DISABLED_BEFORE_IS_WAIT_MS = 1_500L;
	private static final long AFTER_IS_WAIT_MS = 5_000L;
	private static final long RESCAN_COOLDOWN_MS = 8_000L;
	private static final String ISLAND_COMMAND = "/hub";

	private final Set<String> registeredUsernames = new HashSet<>();
	private State state = State.IDLE;
	private boolean previousAreaKnown;
	private boolean wasInTheEnd;
	private boolean scanWhenInEnd;
	private long playerAvailableSince = -1L;
	private long waitStartedAt = -1L;
	private long nextScanAllowedAt = 0L;
	private int collisionSwitchCount;

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
			case WAIT_BEFORE_IS -> tickWaitBeforeIslandCommand(snapshot, commandSink);
			case WAIT_AFTER_IS -> tickWaitAfterIslandCommand(snapshot, macroController, commandSink);
			case WAIT_PLAYER_STABLE -> tickWaitPlayerStable(snapshot, macroController, commandSink);
			case ENSURE_ON -> tickEnsureOn(snapshot, macroController, commandSink, inTheEnd);
		}
	}

	public int collisionSwitchCount() {
		return collisionSwitchCount;
	}

	private void maybeStartSwitch(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink,
		boolean inTheEnd
	) {
		if (!scanWhenInEnd || !inTheEnd || !macroController.desiredOn()
			|| macroController.observedState() != NebulaMacroController.ObservedState.ON
			|| snapshot.nowMs() < nextScanAllowedAt) {
			return;
		}
		Optional<String> collision = visibleRegisteredAccount(snapshot.currentUsername(), snapshot.tabListLines());
		scanWhenInEnd = false;
		if (collision.isEmpty()) {
			return;
		}
		collisionSwitchCount++;
		state = State.ENSURE_OFF;
		tickEnsureOff(snapshot, macroController, commandSink);
	}

	private void tickEnsureOff(Snapshot snapshot, NebulaMacroController macroController, Consumer<String> commandSink) {
		NebulaMacroController.EnsureResult result = macroController.ensureOff(commandSink, snapshot.nowMs());
		if (result != NebulaMacroController.EnsureResult.COMPLETE) {
			return;
		}
		state = State.WAIT_BEFORE_IS;
		waitStartedAt = snapshot.nowMs();
	}

	private void tickWaitBeforeIslandCommand(Snapshot snapshot, Consumer<String> commandSink) {
		if (snapshot.nowMs() - waitStartedAt < DISABLED_BEFORE_IS_WAIT_MS) {
			return;
		}
		commandSink.accept(ISLAND_COMMAND);
		state = State.WAIT_AFTER_IS;
		waitStartedAt = snapshot.nowMs();
		playerAvailableSince = snapshot.playerAvailable() ? snapshot.nowMs() : -1L;
	}

	private void tickWaitAfterIslandCommand(
		Snapshot snapshot,
		NebulaMacroController macroController,
		Consumer<String> commandSink
	) {
		if (snapshot.nowMs() - waitStartedAt < AFTER_IS_WAIT_MS) {
			if (!snapshot.playerAvailable()) {
				playerAvailableSince = -1L;
			} else if (playerAvailableSince < 0L) {
				playerAvailableSince = snapshot.nowMs();
			}
			return;
		}
		state = State.WAIT_PLAYER_STABLE;
		tickWaitPlayerStable(snapshot, macroController, commandSink);
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
		waitStartedAt = -1L;
		nextScanAllowedAt = snapshot.nowMs() + RESCAN_COOLDOWN_MS;
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
		WAIT_BEFORE_IS,
		WAIT_AFTER_IS,
		WAIT_PLAYER_STABLE,
		ENSURE_ON
	}
}
