package com.autoauction.client.transfer;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinAmountParserTest {
	@Test
	void parsesPlainAndSuffixedCoinAmounts() {
		assertEquals(OptionalLong.of(200_000_000L), CoinAmountParser.parse("200m"));
		assertEquals(OptionalLong.of(2_500_000L), CoinAmountParser.parse("2.5m"));
		assertEquals(OptionalLong.of(150_000L), CoinAmountParser.parse("150k"));
		assertEquals(OptionalLong.of(1_200_000_000L), CoinAmountParser.parse("1.2b"));
		assertEquals(OptionalLong.of(200_000_000L), CoinAmountParser.parse("200,000,000"));
	}

	@Test
	void rejectsInvalidOrNonPositiveAmounts() {
		assertTrue(CoinAmountParser.parse("").isEmpty());
		assertTrue(CoinAmountParser.parse("abc").isEmpty());
		assertTrue(CoinAmountParser.parse("0").isEmpty());
		assertTrue(CoinAmountParser.parse("-5m").isEmpty());
	}
}
