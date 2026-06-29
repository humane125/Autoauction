package com.autoauction.client.transfer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SenderParkingTracker {
	private final Map<String, ParkedStacks> parkedByUsername = new HashMap<>();

	public int parkedStacks(String username, String itemName) {
		ParkedStacks parked = parkedByUsername.get(normalizeUsername(username));
		if (parked == null || !parked.matchesItem(itemName)) {
			return 0;
		}
		return parked.stacks();
	}

	public boolean restoreRequiredBeforeRunning(String username, String itemName) {
		ParkedStacks parked = parkedByUsername.get(normalizeUsername(username));
		return parked != null && parked.stacks() > 0 && !parked.matchesItem(itemName);
	}

	public Optional<ParkedStacks> restoreRequest(String username) {
		ParkedStacks parked = parkedByUsername.get(normalizeUsername(username));
		if (parked == null || parked.stacks() <= 0) {
			return Optional.empty();
		}
		return Optional.of(parked);
	}

	public void recordParkedStack(String username, String itemName) {
		String key = normalizeUsername(username);
		if (key.isBlank()) {
			return;
		}
		ParkedStacks parked = parkedByUsername.get(key);
		if (parked == null || !parked.matchesItem(itemName)) {
			parkedByUsername.put(key, new ParkedStacks(itemName, 1));
			return;
		}
		parkedByUsername.put(key, new ParkedStacks(parked.itemName(), parked.stacks() + 1));
	}

	public void recordRestoredStack(String username, String itemName) {
		String key = normalizeUsername(username);
		ParkedStacks parked = parkedByUsername.get(key);
		if (parked == null || !parked.matchesItem(itemName)) {
			return;
		}
		int remaining = Math.max(0, parked.stacks() - 1);
		if (remaining == 0) {
			parkedByUsername.remove(key);
		} else {
			parkedByUsername.put(key, new ParkedStacks(parked.itemName(), remaining));
		}
	}

	public void clear(String username) {
		parkedByUsername.remove(normalizeUsername(username));
	}

	private static String normalizeUsername(String username) {
		return String.valueOf(username == null ? "" : username).trim().toLowerCase(Locale.ROOT);
	}

	public record ParkedStacks(String itemName, int stacks) {
		public ParkedStacks {
			itemName = String.valueOf(itemName == null ? "" : itemName).trim();
			stacks = Math.max(0, stacks);
		}

		private boolean matchesItem(String value) {
			return !itemName.isBlank() && itemName.equalsIgnoreCase(String.valueOf(value == null ? "" : value).trim());
		}
	}
}
