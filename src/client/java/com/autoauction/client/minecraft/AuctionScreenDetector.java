package com.autoauction.client.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

public final class AuctionScreenDetector {
	public boolean isAuctionHouseMain(Minecraft client) {
		return titleContains(client, "Auction House");
	}

	public boolean isCreateAuction(Minecraft client) {
		return titleContains(client, "Create Auction") || titleContains(client, "Create BIN Auction");
	}

	public boolean isConfirmAuction(Minecraft client) {
		return titleContains(client, "Confirm") || titleContains(client, "Confirm Auction");
	}

	public boolean slotNameContains(ItemStack stack, String text) {
		return !stack.isEmpty() && stack.getHoverName().getString().contains(text);
	}

	private boolean titleContains(Minecraft client, String text) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}

		return screen.getTitle().getString().contains(text);
	}
}
