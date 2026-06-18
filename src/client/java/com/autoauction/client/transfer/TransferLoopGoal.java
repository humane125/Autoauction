package com.autoauction.client.transfer;

public final class TransferLoopGoal {
	private final long targetCoins;
	private long totalTransferred;
	private int cycles;
	private long lastPositiveDelta;
	private long startingPurse;
	private long donePurse;
	private boolean hasPurseRange;

	public TransferLoopGoal(long targetCoins) {
		this.targetCoins = Math.max(1, targetCoins);
	}

	public Progress recordCycle(long delta) {
		return recordCycle(delta, 0, delta);
	}

	public Progress recordCycle(long delta, long before, long after) {
		cycles++;
		if (!hasPurseRange) {
			startingPurse = before;
			hasPurseRange = true;
		}
		donePurse = after;
		if (delta > 0) {
			totalTransferred += delta;
			lastPositiveDelta = delta;
		}
		long totalPurseDelta = donePurse - startingPurse;
		long remaining = Math.max(0, targetCoins - totalTransferred);
		int estimatedCyclesRemaining = remaining == 0 || lastPositiveDelta <= 0
			? 0
			: (int) Math.ceil((double) remaining / (double) lastPositiveDelta);
		return new Progress(targetCoins, totalTransferred, remaining, cycles, estimatedCyclesRemaining, totalTransferred >= targetCoins, startingPurse, donePurse, totalPurseDelta);
	}

	public long targetCoins() {
		return targetCoins;
	}

	public long totalTransferred() {
		return totalTransferred;
	}

	public int cycles() {
		return cycles;
	}

	public record Progress(
		long targetCoins,
		long totalTransferred,
		long remaining,
		int cycles,
		int estimatedCyclesRemaining,
		boolean complete,
		long startingPurse,
		long donePurse,
		long totalPurseDelta
	) {
	}
}
