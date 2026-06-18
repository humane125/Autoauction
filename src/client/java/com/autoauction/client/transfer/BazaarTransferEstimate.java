package com.autoauction.client.transfer;

import java.util.Optional;

public record BazaarTransferEstimate(
	String productId,
	int quantity,
	long targetCoins,
	double buyOrderPrice,
	double sellOfferPrice,
	long estimatedDeltaPerCycle,
	int estimatedCycles
) {
	private static final double SELL_OFFER_TAX_RATE = 0.0125D;

	public static Optional<BazaarTransferEstimate> fromPrices(String productId, int quantity, long targetCoins, double buyOrderPrice, double sellOfferPrice) {
		int cleanQuantity = Math.max(1, quantity);
		long cleanTarget = Math.max(1, targetCoins);
		double receiverBuyCost = buyOrderPrice + 0.1D;
		double receiverSellRevenue = Math.max(0D, sellOfferPrice - 0.1D) * (1D - SELL_OFFER_TAX_RATE);
		long estimatedDelta = Math.round((receiverSellRevenue - receiverBuyCost) * cleanQuantity);
		if (estimatedDelta <= 0) {
			return Optional.empty();
		}
		int cycles = (int) Math.ceil((double) cleanTarget / (double) estimatedDelta);
		return Optional.of(new BazaarTransferEstimate(
			String.valueOf(productId == null ? "" : productId),
			cleanQuantity,
			cleanTarget,
			buyOrderPrice,
			sellOfferPrice,
			estimatedDelta,
			cycles
		));
	}
}
