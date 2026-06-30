package com.autoauction.client.stats;

public record SummoningEyeEvent(String action, int quantity, int pricePerEye) {
	public SummoningEyeEvent {
		action = String.valueOf(action == null ? "" : action).trim();
		quantity = Math.max(1, quantity);
		pricePerEye = Math.max(0, pricePerEye);
	}
}
