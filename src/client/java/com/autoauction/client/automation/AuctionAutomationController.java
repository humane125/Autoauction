package com.autoauction.client.automation;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;

import java.util.EnumMap;
import java.util.Map;

public final class AuctionAutomationController {
	private final AutoAuctionConfig config;
	private AutomationState state;
	private EnumMap<ArmorPiece, ArmorSnapshot> triggeredArmor = new EnumMap<>(ArmorPiece.class);

	public AuctionAutomationController(AutoAuctionConfig config) {
		this.config = config;
		this.state = config.enabledByDefault() ? AutomationState.WATCHING_ARMOR : AutomationState.STOPPED;
	}

	public void observeArmor(Map<ArmorPiece, ArmorSnapshot> equippedArmor) {
		if (state != AutomationState.WATCHING_ARMOR || equippedArmor.size() != ArmorPiece.values().length) {
			return;
		}

		boolean allReached = equippedArmor.values().stream().allMatch(armor -> armor.reached(config.killThreshold()));
		if (allReached) {
			triggeredArmor = new EnumMap<>(equippedArmor);
			state = AutomationState.THRESHOLD_REACHED;
		}
	}

	public void start() {
		triggeredArmor.clear();
		state = AutomationState.WATCHING_ARMOR;
	}

	public void stop() {
		triggeredArmor.clear();
		state = AutomationState.STOPPED;
	}

	public boolean isEnabled() {
		return state != AutomationState.STOPPED;
	}

	public AutomationState state() {
		return state;
	}

	public EnumMap<ArmorPiece, ArmorSnapshot> triggeredArmor() {
		return new EnumMap<>(triggeredArmor);
	}
}
