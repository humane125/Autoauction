package com.autoauction.client.automation;

import com.autoauction.client.config.AutoAuctionConfig;
import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionAutomationControllerTest {
	@Test
	void waitsUntilAllFourArmorPiecesReachThreshold() {
		AutoAuctionConfig config = AutoAuctionConfig.defaults().withEnabledByDefault(true);
		AuctionAutomationController controller = new AuctionAutomationController(config);

		EnumMap<ArmorPiece, ArmorSnapshot> armor = fullSet(25_000);
		armor.put(ArmorPiece.BOOTS, new ArmorSnapshot(ArmorPiece.BOOTS, "Final Destination Boots",
			"Final Destination Boots", 24_999, false, 0));

		controller.observeArmor(armor);
		assertEquals(AutomationState.WATCHING_ARMOR, controller.state());

		armor.put(ArmorPiece.BOOTS, new ArmorSnapshot(ArmorPiece.BOOTS, "Final Destination Boots",
			"Final Destination Boots", 25_000, false, 0));
		controller.observeArmor(armor);

		assertEquals(AutomationState.THRESHOLD_REACHED, controller.state());
	}

	@Test
	void startsDisabledByDefaultAndIgnoresArmorUntilStarted() {
		AuctionAutomationController controller = new AuctionAutomationController(AutoAuctionConfig.defaults());

		controller.observeArmor(fullSet(25_000));

		assertEquals(AutomationState.STOPPED, controller.state());

		controller.start();
		controller.observeArmor(fullSet(25_000));

		assertEquals(AutomationState.THRESHOLD_REACHED, controller.state());
	}

	@Test
	void stopCancelsThresholdStateAndClearsTriggeredArmor() {
		AuctionAutomationController controller = new AuctionAutomationController(AutoAuctionConfig.defaults().withEnabledByDefault(true));

		controller.observeArmor(fullSet(25_000));
		controller.stop();

		assertEquals(AutomationState.STOPPED, controller.state());
		assertEquals(0, controller.triggeredArmor().size());
	}

	private EnumMap<ArmorPiece, ArmorSnapshot> fullSet(int kills) {
		EnumMap<ArmorPiece, ArmorSnapshot> armor = new EnumMap<>(ArmorPiece.class);
		for (ArmorPiece piece : ArmorPiece.values()) {
			armor.put(piece, new ArmorSnapshot(piece, piece.baseName(), piece.baseName(), kills, false, 0));
		}
		return armor;
	}
}
