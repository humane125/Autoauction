package com.autoauction.client.transfer;

public record EnderChestParkingPlan(
	int transferQuantity,
	int parkQuantity,
	int requiredParkSlots,
	StopReason stopReason
) {
	public static final int STACK_SIZE = 64;

	public static EnderChestParkingPlan create(
		int inventoryQuantity,
		long senderPurse,
		long reserveCoins,
		double buyBackPricePerItem,
		int emptyEnderChestSlots
	) {
		return createForTarget(inventoryQuantity, senderPurse, reserveCoins, buyBackPricePerItem, 0D, Long.MAX_VALUE, emptyEnderChestSlots);
	}

	public static EnderChestParkingPlan createForTarget(
		int inventoryQuantity,
		long senderPurse,
		long reserveCoins,
		double buyBackPricePerItem,
		double estimatedProfitPerItem,
		long remainingTargetCoins,
		int emptyEnderChestSlots
	) {
		int cleanInventoryQuantity = Math.max(0, inventoryQuantity);
		if (cleanInventoryQuantity < STACK_SIZE) {
			return stop(StopReason.BELOW_STACK);
		}
		if (buyBackPricePerItem <= 0D) {
			return stop(StopReason.NO_PRICE);
		}

		long usableCoins = senderPurse - Math.max(0L, reserveCoins);
		if (usableCoins <= 0) {
			return stop(StopReason.BELOW_STACK);
		}

		int affordableQuantity = roundDownToStack((int) Math.floor(usableCoins / buyBackPricePerItem));
		int inventoryStackQuantity = roundDownToStack(cleanInventoryQuantity);
		int targetQuantity = inventoryStackQuantity;
		if (estimatedProfitPerItem > 0D && remainingTargetCoins > 0L && remainingTargetCoins < Long.MAX_VALUE) {
			targetQuantity = roundUpToStack((int) Math.ceil((double) remainingTargetCoins / estimatedProfitPerItem));
		}

		int transferQuantity = Math.min(inventoryStackQuantity, Math.min(affordableQuantity, targetQuantity));
		transferQuantity = roundDownToStack(transferQuantity);
		if (transferQuantity < STACK_SIZE) {
			return stop(StopReason.BELOW_STACK);
		}

		int parkQuantity = inventoryStackQuantity - transferQuantity;
		int requiredParkSlots = parkQuantity / STACK_SIZE;
		if (requiredParkSlots > Math.max(0, emptyEnderChestSlots)) {
			return stop(StopReason.NOT_ENOUGH_EC_SPACE);
		}
		return new EnderChestParkingPlan(transferQuantity, parkQuantity, requiredParkSlots, StopReason.NONE);
	}

	public boolean canRun() {
		return stopReason == StopReason.NONE;
	}

	private static EnderChestParkingPlan stop(StopReason reason) {
		return new EnderChestParkingPlan(0, 0, 0, reason);
	}

	private static int roundDownToStack(int quantity) {
		return Math.max(0, quantity / STACK_SIZE * STACK_SIZE);
	}

	private static int roundUpToStack(int quantity) {
		if (quantity <= 0) {
			return STACK_SIZE;
		}
		return (int) Math.ceil((double) quantity / (double) STACK_SIZE) * STACK_SIZE;
	}

	public enum StopReason {
		NONE,
		BELOW_STACK,
		NO_PRICE,
		NOT_ENOUGH_EC_SPACE
	}
}
