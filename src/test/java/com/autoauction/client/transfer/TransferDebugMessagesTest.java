package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferDebugMessagesTest {
	@Test
	void formatsTransferStepWithRolePhaseItemAndQuantity() {
		assertEquals(
			"AutoAuction transfer [receiver/buy-order] qty=64 item=Glowstone Distillate :: click slot 15 Create Buy Order",
			TransferDebugMessages.step("receiver", "buy-order", 64, "Glowstone Distillate", "click slot 15 Create Buy Order")
		);
	}

	@Test
	void formatsTransferStateWithDelay() {
		assertEquals(
			"AutoAuction transfer [sender/instant-buy] state=WAIT_AMOUNT_SCREEN qty=64 item=Glowstone Distillate nextDelayMs=400",
			TransferDebugMessages.state("sender", "instant-buy", "WAIT_AMOUNT_SCREEN", 64, "Glowstone Distillate", 400)
		);
	}

	@Test
	void formatsPurseWaitWithBeforeAfterAndDelta() {
		assertEquals(
			"AutoAuction transfer [receiver/claim-coins] waiting for purse update: before=1,773,644 current=1,605,426 delta=-168,218",
			TransferDebugMessages.purseWait("receiver", "claim-coins", 1_773_644L, 1_605_426L, -168_218L)
		);
	}
}
