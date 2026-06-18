package com.autoauction.client.transfer;

import java.util.Optional;
import java.util.OptionalLong;

public final class TransferPurseTracker {
	private Snapshot active;

	public void start(String role, OptionalLong purse) {
		active = purse.isPresent() ? new Snapshot(String.valueOf(role == null ? "" : role), purse.getAsLong()) : null;
	}

	public void clear() {
		active = null;
	}

	public Optional<Summary> finish(OptionalLong purse) {
		Snapshot snapshot = active;
		active = null;
		return summarize(snapshot, purse);
	}

	public Optional<Summary> preview(OptionalLong purse) {
		return summarize(active, purse);
	}

	private Optional<Summary> summarize(Snapshot snapshot, OptionalLong purse) {
		if (snapshot == null || purse.isEmpty()) {
			return Optional.empty();
		}
		long after = purse.getAsLong();
		return Optional.of(new Summary(snapshot.role(), snapshot.before(), after, after - snapshot.before()));
	}

	private record Snapshot(String role, long before) {
	}

	public record Summary(String role, long before, long after, long delta) {
	}
}
