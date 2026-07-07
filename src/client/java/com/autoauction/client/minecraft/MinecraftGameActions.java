package com.autoauction.client.minecraft;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.domain.ArmorSnapshot;
import com.autoauction.client.item.FinalDestinationParser;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class MinecraftGameActions {
	private static final int PLAYER_HELMET_SLOT = 5;
	private static final int PLAYER_CHESTPLATE_SLOT = 6;
	private static final int PLAYER_LEGGINGS_SLOT = 7;
	private static final int PLAYER_BOOTS_SLOT = 8;

	private final FinalDestinationParser parser = new FinalDestinationParser();

	public EnumMap<ArmorPiece, ArmorSnapshot> readEquippedFinalDestinationArmor(Minecraft client) {
		EnumMap<ArmorPiece, ArmorSnapshot> result = new EnumMap<>(ArmorPiece.class);
		read(client, EquipmentSlot.HEAD, ArmorPiece.HELMET).ifPresent(value -> result.put(ArmorPiece.HELMET, value));
		read(client, EquipmentSlot.CHEST, ArmorPiece.CHESTPLATE).ifPresent(value -> result.put(ArmorPiece.CHESTPLATE, value));
		read(client, EquipmentSlot.LEGS, ArmorPiece.LEGGINGS).ifPresent(value -> result.put(ArmorPiece.LEGGINGS, value));
		read(client, EquipmentSlot.FEET, ArmorPiece.BOOTS).ifPresent(value -> result.put(ArmorPiece.BOOTS, value));
		return result;
	}

	public EnumMap<ArmorPiece, String> readEquippedArmorNames(Minecraft client) {
		EnumMap<ArmorPiece, String> result = new EnumMap<>(ArmorPiece.class);
		readEquippedName(client, EquipmentSlot.HEAD).ifPresent(value -> result.put(ArmorPiece.HELMET, value));
		readEquippedName(client, EquipmentSlot.CHEST).ifPresent(value -> result.put(ArmorPiece.CHESTPLATE, value));
		readEquippedName(client, EquipmentSlot.LEGS).ifPresent(value -> result.put(ArmorPiece.LEGGINGS, value));
		readEquippedName(client, EquipmentSlot.FEET).ifPresent(value -> result.put(ArmorPiece.BOOTS, value));
		return result;
	}

	public void sendChatCommand(Minecraft client, String command) {
		String normalized = normalizedCommand(command);
		if (client.player == null || normalized.isBlank()) {
			return;
		}

		client.player.connection.sendCommand(normalized);
	}

	public void sendChatMessage(Minecraft client, String message) {
		String normalized = String.valueOf(message == null ? "" : message).trim();
		if (client.player == null || normalized.isBlank()) {
			return;
		}

		client.player.connection.sendChat(normalized);
	}

	public boolean sendClientCommand(Minecraft client, String command) {
		String normalized = normalizedCommand(command);
		if (normalized.isBlank() || client.getConnection() == null) {
			return false;
		}
		return ClientCommandInternals.executeCommand(normalized);
	}

	static String normalizedCommand(String command) {
		String normalized = String.valueOf(command == null ? "" : command).trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	public boolean hasEmptyInventorySlots(Minecraft client, int requiredSlots) {
		if (client.player == null) {
			return false;
		}

		int emptySlots = 0;
		for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
			if (client.player.getInventory().getItem(slot).isEmpty()) {
				emptySlots++;
			}
		}
		return emptySlots >= requiredSlots;
	}

	public int countInventoryItemsByName(Minecraft client, String itemNamePart) {
		if (client.player == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = client.player.getInventory().getItem(slot);
			if (!stack.isEmpty() && itemNameMatches(stack.getHoverName().getString(), itemNamePart)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	public void quickMoveEquippedArmorToInventory(Minecraft client) {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		int containerId = client.player.containerMenu.containerId;
		client.gameMode.handleContainerInput(containerId, PLAYER_HELMET_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
		client.gameMode.handleContainerInput(containerId, PLAYER_CHESTPLATE_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
		client.gameMode.handleContainerInput(containerId, PLAYER_LEGGINGS_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
		client.gameMode.handleContainerInput(containerId, PLAYER_BOOTS_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
	}

	public void openInventory(Minecraft client) {
		if (client.player != null && !(client.screen instanceof InventoryScreen)) {
			client.setScreen(new InventoryScreen(client.player));
		}
	}

	public boolean isInventoryOpen(Minecraft client) {
		return client.screen instanceof InventoryScreen;
	}

	public void closeScreen(Minecraft client) {
		if (client.player != null && client.screen != null) {
			client.player.closeContainer();
			return;
		}
		client.setScreen(null);
	}

	public int equippedArmorCount(Minecraft client) {
		if (client.player == null) {
			return 0;
		}

		int count = 0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			if (!client.player.getItemBySlot(slot).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	public boolean isArmorPieceEquipped(Minecraft client, ArmorPiece piece) {
		if (client.player == null) {
			return false;
		}

		return !client.player.getItemBySlot(toEquipmentSlot(piece)).isEmpty();
	}

	public boolean isFinalDestinationArmorEquipped(Minecraft client, ArmorPiece piece) {
		if (client.player == null) {
			return false;
		}
		ItemStack equipped = client.player.getItemBySlot(toEquipmentSlot(piece));
		return !equipped.isEmpty() && isFinalDestinationArmorName(piece, equipped.getHoverName().getString());
	}

	public boolean hasEquippedFinalDestinationArmorSet(Minecraft client) {
		for (ArmorPiece piece : ArmorPiece.values()) {
			if (!isFinalDestinationArmorEquipped(client, piece)) {
				return false;
			}
		}
		return true;
	}

	public static String finalDestinationBaseName(ArmorPiece piece) {
		return switch (piece) {
			case HELMET -> "Final Destination Helmet";
			case CHESTPLATE -> "Final Destination Chestplate";
			case LEGGINGS -> "Final Destination Leggings";
			case BOOTS -> "Final Destination Boots";
		};
	}

	public boolean equipFinalDestinationArmorFromInventory(Minecraft client) {
		if (client.player == null) {
			return false;
		}
		openInventory(client);
		boolean movedAny = false;
		for (ArmorPiece piece : ArmorPiece.values()) {
			if (isFinalDestinationArmorEquipped(client, piece)) {
				continue;
			}
			Optional<Integer> slot = findInventoryHandlerSlotByItemName(client, finalDestinationBaseName(piece), 1);
			if (slot.isPresent()) {
				quickMoveSlot(client, slot.get());
				movedAny = true;
			}
		}
		return movedAny;
	}

	static boolean isFinalDestinationArmorName(ArmorPiece piece, String itemName) {
		return itemNameMatches(itemName, finalDestinationBaseName(piece));
	}

	public void quickMoveArmorPieceToInventory(Minecraft client, ArmorPiece piece) {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		client.gameMode.handleContainerInput(client.player.containerMenu.containerId, toArmorHandlerSlot(piece), 0, ContainerInput.QUICK_MOVE, client.player);
	}

	public void clickSlot(Minecraft client, int handlerSlot) {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		client.gameMode.handleContainerInput(client.player.containerMenu.containerId, handlerSlot, 0, ContainerInput.PICKUP, client.player);
	}

	public void rightClickSlot(Minecraft client, int handlerSlot) {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		client.gameMode.handleContainerInput(client.player.containerMenu.containerId, handlerSlot, 1, ContainerInput.PICKUP, client.player);
	}

	public void quickMoveSlot(Minecraft client, int handlerSlot) {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		client.gameMode.handleContainerInput(client.player.containerMenu.containerId, handlerSlot, 0, ContainerInput.QUICK_MOVE, client.player);
	}

	public boolean screenTitleContains(Minecraft client, String text) {
		return client.screen != null && client.screen.getTitle().getString().contains(text);
	}

	public boolean isBazaarOpen(Minecraft client) {
		return screenTitleMatches(client, MinecraftGameActions::isBazaarTitle);
	}

	public boolean isEnderChestOpen(Minecraft client) {
		return screenTitleContains(client, "Ender Chest");
	}

	public boolean isSellInventoryConfirmOpen(Minecraft client) {
		return screenTitleMatches(client, MinecraftGameActions::isSellInventoryConfirmTitle);
	}

	public Optional<Integer> findSellInventoryButtonSlot(Minecraft client) {
		return findHandlerSlotByItemNameMatching(client, MinecraftGameActions::isSellInventoryButtonName);
	}

	public Optional<Integer> findSellInventoryConfirmSlot(Minecraft client) {
		return findHandlerSlotByItemNameMatching(client, MinecraftGameActions::isSellInventoryConfirmName);
	}

	public Optional<Integer> findCreateAuctionButtonSlot(Minecraft client) {
		return findHandlerSlotByItemNameMatching(client, MinecraftGameActions::isCreateAuctionButtonName);
	}

	private boolean screenTitleMatches(Minecraft client, Predicate<String> matcher) {
		return client.screen != null && matcher.test(client.screen.getTitle().getString());
	}

	public Optional<String> itemNameAt(Minecraft client, int handlerSlot) {
		if (client.player == null || handlerSlot < 0 || handlerSlot >= client.player.containerMenu.slots.size()) {
			return Optional.empty();
		}

		ItemStack stack = client.player.containerMenu.slots.get(handlerSlot).getItem();
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(stack.getHoverName().getString());
	}

	public int itemCountAt(Minecraft client, int handlerSlot) {
		if (client.player == null || handlerSlot < 0 || handlerSlot >= client.player.containerMenu.slots.size()) {
			return 0;
		}

		return client.player.containerMenu.slots.get(handlerSlot).getItem().getCount();
	}

	public boolean slotContainsAtLeast(Minecraft client, int handlerSlot, String itemNamePart, int minimumCount) {
		if (client.player == null || handlerSlot < 0 || handlerSlot >= client.player.containerMenu.slots.size()) {
			return false;
		}

		ItemStack stack = client.player.containerMenu.slots.get(handlerSlot).getItem();
		return !stack.isEmpty()
			&& stack.getCount() >= minimumCount
			&& itemNameMatches(stack.getHoverName().getString(), itemNamePart);
	}

	public boolean carriedItemMatches(Minecraft client, String itemNamePart) {
		if (client.player == null) {
			return false;
		}

		ItemStack carried = client.player.containerMenu.getCarried();
		return !carried.isEmpty() && itemNameMatches(carried.getHoverName().getString(), itemNamePart);
	}

	public boolean carriedItemEmpty(Minecraft client) {
		return client.player == null || client.player.containerMenu.getCarried().isEmpty();
	}

	public Optional<Integer> findInventoryHandlerSlotByItemName(Minecraft client, String itemNamePart, int minimumCount) {
		if (client.player == null) {
			return Optional.empty();
		}

		for (int handlerSlot : playerInventoryHandlerSlots(client)) {
			ItemStack stack = client.player.containerMenu.slots.get(handlerSlot).getItem();
			if (!stack.isEmpty()
				&& stack.getCount() >= minimumCount
				&& itemNameMatches(stack.getHoverName().getString(), itemNamePart)) {
				return Optional.of(handlerSlot);
			}
		}
		return Optional.empty();
	}

	public Optional<Integer> findEmptyInventoryHandlerSlot(Minecraft client) {
		if (client.player == null) {
			return Optional.empty();
		}

		for (int handlerSlot : playerInventoryHandlerSlots(client)) {
			if (client.player.containerMenu.slots.get(handlerSlot).getItem().isEmpty()) {
				return Optional.of(handlerSlot);
			}
		}
		return Optional.empty();
	}

	public Optional<Integer> findHandlerSlotByItemName(Minecraft client, String itemNamePart) {
		return findHandlerSlotByItemNameMatching(client, itemName -> itemNameMatches(itemName, itemNamePart));
	}

	public Optional<Integer> findHandlerSlotByExactItemName(Minecraft client, String itemName) {
		return findHandlerSlotByItemNameMatching(client, candidate -> itemNameExactlyMatches(candidate, itemName));
	}

	public List<Integer> emptyHandlerSlotsInRange(Minecraft client, int firstHandlerSlot, int lastHandlerSlot) {
		List<Integer> slots = new ArrayList<>();
		if (client.player == null) {
			return slots;
		}
		int end = Math.min(lastHandlerSlot, client.player.containerMenu.slots.size() - 1);
		for (int handlerSlot = Math.max(0, firstHandlerSlot); handlerSlot <= end; handlerSlot++) {
			if (client.player.containerMenu.slots.get(handlerSlot).getItem().isEmpty()) {
				slots.add(handlerSlot);
			}
		}
		return slots;
	}

	public List<Integer> matchingFullStackHandlerSlotsInRange(Minecraft client, String itemNamePart, int firstHandlerSlot, int lastHandlerSlot) {
		List<Integer> slots = new ArrayList<>();
		if (client.player == null) {
			return slots;
		}
		int end = Math.min(lastHandlerSlot, client.player.containerMenu.slots.size() - 1);
		for (int handlerSlot = Math.max(0, firstHandlerSlot); handlerSlot <= end; handlerSlot++) {
			ItemStack stack = client.player.containerMenu.slots.get(handlerSlot).getItem();
			if (!stack.isEmpty() && stack.getCount() >= stack.getMaxStackSize() && itemNameMatches(stack.getHoverName().getString(), itemNamePart)) {
				slots.add(handlerSlot);
			}
		}
		return slots;
	}

	private Optional<Integer> findHandlerSlotByItemNameMatching(Minecraft client, Predicate<String> matcher) {
		if (client.player == null) {
			return Optional.empty();
		}

		for (int handlerSlot = 0; handlerSlot < client.player.containerMenu.slots.size(); handlerSlot++) {
			ItemStack stack = client.player.containerMenu.slots.get(handlerSlot).getItem();
			if (!stack.isEmpty() && matcher.test(stack.getHoverName().getString())) {
				return Optional.of(handlerSlot);
			}
		}
		return Optional.empty();
	}

	private List<Integer> playerInventoryHandlerSlots(Minecraft client) {
		List<Integer> slots = new ArrayList<>();
		if (client.player == null) {
			return slots;
		}
		for (int handlerSlot = 0; handlerSlot < client.player.containerMenu.slots.size(); handlerSlot++) {
			Slot slot = client.player.containerMenu.slots.get(handlerSlot);
			if (slot.container == client.player.getInventory()) {
				slots.add(handlerSlot);
			}
		}
		return slots;
	}

	static boolean isBazaarTitle(String title) {
		return containsIgnoreCase(title, "Bazaar");
	}

	static boolean isSellInventoryButtonName(String itemName) {
		return containsIgnoreCase(itemName, "Sell Inventory");
	}

	static boolean isSellInventoryConfirmTitle(String title) {
		return containsIgnoreCase(title, "Are you sure?");
	}

	static boolean isSellInventoryConfirmName(String itemName) {
		return containsIgnoreCase(itemName, "Selling whole inventory");
	}

	static boolean isCreateAuctionButtonName(String itemName) {
		return itemNameExactlyMatches(itemName, "Create Auction");
	}

	static boolean itemNameMatches(String itemName, String expectedPart) {
		String expected = comparableItemName(expectedPart);
		return !expected.isBlank() && comparableItemName(itemName).contains(expected);
	}

	static boolean itemNameExactlyMatches(String itemName, String expectedName) {
		String expected = comparableItemName(expectedName);
		return !expected.isBlank() && comparableItemName(itemName).equals(expected);
	}

	private static boolean containsIgnoreCase(String value, String expectedPart) {
		return String.valueOf(value == null ? "" : value)
			.toLowerCase(Locale.ROOT)
			.contains(String.valueOf(expectedPart == null ? "" : expectedPart).toLowerCase(Locale.ROOT));
	}

	private static String comparableItemName(String value) {
		return String.valueOf(value == null ? "" : value)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]", "");
	}

	public boolean submitSignText(Minecraft client, String text) {
		if (!(client.screen instanceof AbstractSignEditScreen screen)) {
			return false;
		}

		try {
			Method setMessage = AbstractSignEditScreen.class.getDeclaredMethod("setMessage", String.class);
			setMessage.setAccessible(true);
			setMessage.invoke(screen, text);

			Method onDone = AbstractSignEditScreen.class.getDeclaredMethod("onDone");
			onDone.setAccessible(true);
			onDone.invoke(screen);
			return true;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to submit sign text", e);
		}
	}

	public void disconnect(Minecraft client, String reason) {
		if (client.getConnection() != null) {
			client.disconnectFromWorld(Component.literal(reason));
		}
	}

	public void connectToServer(Minecraft client, String address) {
		ServerData server = new ServerData("Hypixel", address, ServerData.Type.OTHER);
		Screen parent = client.screen == null ? new TitleScreen() : client.screen;
		ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(address), server, false, null);
	}

	public void closeInstance(Minecraft client) {
		client.stop();
	}

	public List<String> describeOpenContainerSlots(Minecraft client) {
		List<String> lines = new ArrayList<>();
		if (client.player == null) {
			lines.add("No player is loaded.");
			return lines;
		}

		String title = client.screen == null ? "none" : client.screen.getTitle().getString();
		AbstractContainerMenu menu = client.player.containerMenu;
		lines.add("screenTitle=\"" + title + "\" containerId=" + menu.containerId + " slotCount=" + menu.slots.size());
		for (int handlerSlot = 0; handlerSlot < menu.slots.size(); handlerSlot++) {
			Slot slot = menu.slots.get(handlerSlot);
			ItemStack item = slot.getItem();
			String itemName = item.isEmpty() ? "EMPTY" : item.getHoverName().getString();
			lines.add("handlerSlot=" + handlerSlot
				+ " containerSlot=" + slot.getContainerSlot()
				+ " x=" + slot.x
				+ " y=" + slot.y
				+ " item=\"" + itemName + "\"");
		}
		return lines;
	}

	private Optional<ArmorSnapshot> read(Minecraft client, EquipmentSlot slot, ArmorPiece piece) {
		if (client.player == null) {
			return Optional.empty();
		}

		ItemStack stack = client.player.getItemBySlot(slot);
		String displayName = stack.getHoverName().getString();
		List<String> lore = Screen.getTooltipFromItem(client, stack).stream().map(Component::getString).toList();
		return parser.parse(piece, displayName, lore);
	}

	private Optional<String> readEquippedName(Minecraft client, EquipmentSlot slot) {
		if (client.player == null) {
			return Optional.empty();
		}

		ItemStack stack = client.player.getItemBySlot(slot);
		return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getHoverName().getString());
	}

	private EquipmentSlot toEquipmentSlot(ArmorPiece piece) {
		return switch (piece) {
			case HELMET -> EquipmentSlot.HEAD;
			case CHESTPLATE -> EquipmentSlot.CHEST;
			case LEGGINGS -> EquipmentSlot.LEGS;
			case BOOTS -> EquipmentSlot.FEET;
		};
	}

	private int toArmorHandlerSlot(ArmorPiece piece) {
		return switch (piece) {
			case HELMET -> PLAYER_HELMET_SLOT;
			case CHESTPLATE -> PLAYER_CHESTPLATE_SLOT;
			case LEGGINGS -> PLAYER_LEGGINGS_SLOT;
			case BOOTS -> PLAYER_BOOTS_SLOT;
		};
	}
}
