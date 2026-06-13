package com.autoauction.client.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceTextFormatterTest {
	@Test
	void formatsWholeMillions() {
		assertEquals("30m", PriceTextFormatter.forSign(30_000_000));
		assertEquals("26m", PriceTextFormatter.forSign(26_000_000));
	}

	@Test
	void formatsDecimalMillions() {
		assertEquals("2.5m", PriceTextFormatter.forSign(2_500_000));
	}

	@Test
	void formatsThousands() {
		assertEquals("500k", PriceTextFormatter.forSign(500_000));
	}

	@Test
	void fallsBackToFullNumber() {
		assertEquals("1234567", PriceTextFormatter.forSign(1_234_567));
	}
}
