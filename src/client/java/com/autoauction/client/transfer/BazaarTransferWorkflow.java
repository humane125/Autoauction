package com.autoauction.client.transfer;

import java.util.Locale;

public final class BazaarTransferWorkflow {
	public static final int CREATE_BUY_ORDER_SLOT = 15;
	public static final int CUSTOM_AMOUNT_SLOT = 16;
	public static final int TOP_ORDER_PLUS_0_1_SLOT = 12;
	public static final int CONFIRM_BUY_ORDER_SLOT = 13;
	public static final int SELL_INSTANTLY_SLOT = 11;
	public static final int INSTANT_SELL_WARNING_SLOT = 13;
	public static final int MANAGE_ORDERS_SLOT = 50;
	public static final int CREATE_SELL_OFFER_SLOT = 16;
	public static final int BEST_OFFER_MINUS_0_1_SLOT = 12;
	public static final int CONFIRM_SELL_OFFER_SLOT = 13;
	public static final int BUY_INSTANTLY_SLOT = 10;
	public static final int CONFIRM_INSTANT_BUY_SLOT = 13;
	public static final int CLAIM_ALL_COINS_SLOT = 32;

	private BazaarTransferWorkflow() {
	}

	public static boolean isItemPage(String title, String itemName) {
		String cleanTitle = normalized(title);
		String cleanTitleItem = comparable(title);
		String cleanItem = comparable(itemName);
		return !cleanItem.isBlank()
			&& !isBazaarResultScreen(title)
			&& (cleanTitleItem.contains(cleanItem) || itemNameMatchesTitleTarget(title, itemName))
			&& !cleanTitle.contains("instant buy")
			&& !cleanTitle.contains("instant sell");
	}

	public static boolean isBazaarResultScreen(String title) {
		return normalized(title).startsWith("bazaar");
	}

	public static boolean isBuyOrderAmountScreen(String title) {
		return normalized(title).contains("how many do you want");
	}

	public static boolean isBuyOrderPriceScreen(String title) {
		return normalized(title).contains("how much do you want to pay");
	}

	public static boolean isConfirmBuyOrderScreen(String title) {
		return normalized(title).equals("confirm buy order");
	}

	public static boolean isInstantSellWarningScreen(String title) {
		return normalized(title).equals("confirm");
	}

	public static boolean isInstantBuyAmountScreen(String title) {
		String cleanTitle = normalized(title);
		return !cleanTitle.equals("confirm instant buy")
			&& (cleanTitle.contains("instant buy") || cleanTitle.contains("➜ instan") || cleanTitle.contains("âžœ instan"));
	}

	public static boolean isConfirmInstantBuyScreen(String title) {
		return normalized(title).equals("confirm instant buy");
	}

	public static boolean isOrdersScreen(String title) {
		return normalized(title).equals("your bazaar orders");
	}

	public static boolean isSellOfferPriceScreen(String title) {
		return normalized(title).contains("at what price are you selling");
	}

	public static boolean isConfirmSellOfferScreen(String title) {
		return normalized(title).equals("confirm sell offer");
	}

	public static boolean isSignScreen(String title) {
		return normalized(title).contains("edit sign message");
	}

	public static boolean isBuyOrderFilledMessage(String message, String itemName) {
		String clean = cleanChatMessage(message);
		String comparableClean = comparable(clean);
		String comparableItem = comparable(itemName);
		return !comparableItem.isBlank()
			&& comparableClean.contains("bazaar")
			&& comparableClean.contains("yourbuyorderfor")
			&& comparableClean.contains(comparableItem)
			&& comparableClean.contains("wasfilled");
	}

	public static boolean isSellOfferFilledMessage(String message, String itemName) {
		String clean = cleanChatMessage(message);
		String comparableClean = comparable(clean);
		String comparableItem = comparable(itemName);
		return !comparableItem.isBlank()
			&& comparableClean.contains("bazaar")
			&& comparableClean.contains("yoursellofferfor")
			&& comparableClean.contains(comparableItem)
			&& comparableClean.contains("wasfilled");
	}

	public static String cleanChatMessage(String message) {
		return String.valueOf(message == null ? "" : message)
			.replaceAll("(?i)\u00a7[0-9A-FK-ORX]", "")
			.replaceAll("\\s+", " ")
			.trim();
	}

	public static String quantityText(int quantity) {
		return Integer.toString(Math.max(1, quantity));
	}

	private static String normalized(String value) {
		return String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
	}

	private static String comparable(String value) {
		return normalized(value).replaceAll("[^a-z0-9]", "");
	}

	private static boolean itemNameMatchesTitleTarget(String title, String itemName) {
		String target = comparable(titleTarget(title));
		String item = comparable(itemName);
		return !target.isBlank() && !item.isBlank() && item.startsWith(target);
	}

	private static String titleTarget(String title) {
		String value = String.valueOf(title == null ? "" : title);
		int arrow = value.lastIndexOf('➜');
		if (arrow >= 0) {
			return value.substring(arrow + 1).trim();
		}
		int asciiArrow = value.lastIndexOf("->");
		if (asciiArrow >= 0) {
			return value.substring(asciiArrow + 2).trim();
		}
		return value.trim();
	}
}
