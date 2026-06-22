package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferAccountListRequestsTest {
	@Test
	void consumesOnlyResponsesMarkedAsSilent() {
		TransferAccountListRequests requests = new TransferAccountListRequests();

		assertFalse(requests.consumeSilentResponse());

		requests.markSilentRequestSent();
		requests.markSilentRequestSent();

		assertTrue(requests.consumeSilentResponse());
		assertTrue(requests.consumeSilentResponse());
		assertFalse(requests.consumeSilentResponse());
	}
}
