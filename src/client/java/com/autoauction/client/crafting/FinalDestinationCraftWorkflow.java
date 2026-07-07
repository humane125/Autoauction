package com.autoauction.client.crafting;

import com.autoauction.client.domain.ArmorPiece;
import com.autoauction.client.minecraft.MinecraftGameActions;
import com.autoauction.client.transfer.BazaarTransferWorkflow;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class FinalDestinationCraftWorkflow {
	private static final int MIN_CLICK_DELAY_MS = 100;
	private static final int CRAFT_MOVE_DELAY_MS = 150;
	private static final int WARNING_CONFIRM_DELAY_MS = 6_000;
	private static final int INSTANT_BUY_CONFIRM_RETRY_DELAY_MS = 1_500;
	private static final int INSTANT_BUY_CONFIRM_MAX_CLICKS = 3;

	private final MinecraftGameActions actions;
	private final int clickDelayMs;
	private final int screenTimeoutMs;
	private final Consumer<String> feedback;
	private final Consumer<String> errorHandler;
	private final Runnable finishHandler;
	private final List<ArmorPiece> craftOrder = FinalDestinationCraftingPlan.craftOrder();

	private State state = State.CHECK_MATERIALS;
	private CraftInstantBuyWorkflow buyWorkflow;
	private int pieceIndex;
	private int placementIndex;
	private FinalDestinationCraftingPlan.Placement currentPlacement;
	private int currentSourceSlot = -1;
	private int currentOutputSlot = -1;
	private int outputCountBeforeTake;
	private long nextActionAt;
	private long stateStartedAt;

	public FinalDestinationCraftWorkflow(
		MinecraftGameActions actions,
		int clickDelayMs,
		int screenTimeoutMs,
		Consumer<String> feedback,
		Consumer<String> errorHandler,
		Runnable finishHandler
	) {
		this.actions = actions;
		this.clickDelayMs = clickDelayMs;
		this.screenTimeoutMs = screenTimeoutMs;
		this.feedback = feedback;
		this.errorHandler = errorHandler;
		this.finishHandler = finishHandler;
	}

	static boolean canReuseCarriedForNextPlacement(FinalDestinationCraftingPlan.Recipe recipe, int currentPlacementIndex) {
		List<FinalDestinationCraftingPlan.Placement> placements = recipe.placements();
		if (currentPlacementIndex < 0 || currentPlacementIndex >= placements.size() - 1) {
			return false;
		}
		FinalDestinationCraftingPlan.Placement current = placements.get(currentPlacementIndex);
		FinalDestinationCraftingPlan.Placement next = placements.get(currentPlacementIndex + 1);
		return current.count() == 1
			&& next.count() == 1
			&& current.ingredient() == next.ingredient();
	}

	public void start(Minecraft client) {
		feedback.accept("AutoAuction FD crafter started.");
		transition(State.CHECK_MATERIALS, client, MIN_CLICK_DELAY_MS);
	}

	public void tick(Minecraft client) {
		if (state == State.DONE || state == State.ERROR || System.currentTimeMillis() < nextActionAt) {
			return;
		}
		try {
			runState(client);
		} catch (Exception e) {
			fail("FD crafting workflow crashed: " + e.getMessage());
		}
	}

	private void runState(Minecraft client) {
		switch (state) {
			case CHECK_MATERIALS -> checkMaterials(client);
			case BUY_NULL_OVOID -> runBuyWorkflow(client, FinalDestinationCraftingPlan.NULL_OVOID);
			case BUY_REDSTONE -> runBuyWorkflow(client, FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST);
			case OPEN_CRAFT -> {
				actions.closeScreen(client);
				actions.sendChatCommand(client, "/craft");
				transition(State.WAIT_CRAFT, client);
			}
			case WAIT_CRAFT -> {
				if (actions.screenTitleContains(client, "Craft Item")) {
					transition(State.START_PIECE, client);
					return;
				}
				timeout("Craft Item screen did not open.");
			}
			case START_PIECE -> startPiece(client);
			case PICK_INGREDIENT -> pickIngredient(client);
			case VERIFY_PICKED_INGREDIENT -> verifyPickedIngredient(client);
			case PLACE_INGREDIENT -> placeIngredient(client);
			case VERIFY_PLACEMENT -> verifyPlacement(client);
			case RETURN_CARRIED -> returnCarriedIngredient(client);
			case VERIFY_RETURNED -> verifyReturnedIngredient(client);
			case VERIFY_RECIPE -> verifyRecipe(client);
			case TAKE_OUTPUT -> takeOutput(client);
			case VERIFY_OUTPUT_TAKEN -> verifyOutputTaken(client);
			case PLACE_OUTPUT -> placeOutput(client);
			case VERIFY_OUTPUT_PLACED -> verifyOutputPlaced(client);
			case DONE, ERROR -> {
			}
		}
	}

	private void checkMaterials(Minecraft client) {
		int currentRedstone = actions.countInventoryItemsByName(client, FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST);
		int currentNullOvoid = actions.countInventoryItemsByName(client, FinalDestinationCraftingPlan.NULL_OVOID);
		FinalDestinationCraftingPlan.Materials missing = FinalDestinationCraftingPlan.missingMaterials(currentRedstone, currentNullOvoid);
		int requiredEmptySlots = requiredEmptySlots(missing);
		if (requiredEmptySlots > 0 && !actions.hasEmptyInventorySlots(client, requiredEmptySlots)) {
			fail("FD crafter needs at least " + requiredEmptySlots + " empty inventory slots for missing Bazaar materials.");
			return;
		}
		if (missing.nullOvoid() > 0) {
			buyWorkflow = new CraftInstantBuyWorkflow(FinalDestinationCraftingPlan.NULL_OVOID, missing.nullOvoid(), FinalDestinationCraftingPlan.NULL_OVOID_REQUIRED);
			buyWorkflow.start(client);
			transition(State.BUY_NULL_OVOID, client);
			return;
		}
		if (missing.enchantedRedstoneDust() > 0) {
			buyWorkflow = new CraftInstantBuyWorkflow(
				FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST,
				missing.enchantedRedstoneDust(),
				FinalDestinationCraftingPlan.ENCHANTED_REDSTONE_DUST_REQUIRED
			);
			buyWorkflow.start(client);
			transition(State.BUY_REDSTONE, client);
			return;
		}
		transition(State.OPEN_CRAFT, client);
	}

	private int requiredEmptySlots(FinalDestinationCraftingPlan.Materials missing) {
		return stackSlots(missing.enchantedRedstoneDust()) + stackSlots(missing.nullOvoid());
	}

	private int stackSlots(int amount) {
		return amount <= 0 ? 0 : (int) Math.ceil(amount / 64.0);
	}

	private void runBuyWorkflow(Minecraft client, String itemName) {
		if (buyWorkflow == null) {
			fail("FD crafter lost active Bazaar buy workflow for " + itemName + ".");
			return;
		}
		buyWorkflow.tick(client);
		if (buyWorkflow.failed()) {
			fail(buyWorkflow.errorMessage());
			return;
		}
		if (!buyWorkflow.done()) {
			return;
		}
		buyWorkflow = null;
		transition(itemName.equals(FinalDestinationCraftingPlan.NULL_OVOID) ? State.CHECK_MATERIALS : State.OPEN_CRAFT, client);
	}

	private void startPiece(Minecraft client) {
		if (pieceIndex >= craftOrder.size()) {
			actions.closeScreen(client);
			done();
			return;
		}
		ArmorPiece piece = craftOrder.get(pieceIndex);
		feedback.accept("AutoAuction FD crafter placing " + piece.baseName() + " recipe.");
		placementIndex = 0;
		transition(State.PICK_INGREDIENT, client);
	}

	private void pickIngredient(Minecraft client) {
		ArmorPiece piece = craftOrder.get(pieceIndex);
		FinalDestinationCraftingPlan.Recipe recipe = FinalDestinationCraftingPlan.recipe(piece);
		if (placementIndex >= recipe.placements().size()) {
			transition(State.VERIFY_RECIPE, client);
			return;
		}
		currentPlacement = recipe.placements().get(placementIndex);
		Optional<Integer> sourceSlot = actions.findInventoryHandlerSlotByItemName(
			client,
			currentPlacement.ingredient().itemName(),
			currentPlacement.count()
		);
		if (sourceSlot.isEmpty()) {
			fail("Could not find " + currentPlacement.count() + "x " + currentPlacement.ingredient().itemName() + " for " + piece.baseName() + ".");
			return;
		}
		currentSourceSlot = sourceSlot.get();
		actions.clickSlot(client, currentSourceSlot);
		transitionCraft(State.VERIFY_PICKED_INGREDIENT, client);
	}

	private void verifyPickedIngredient(Minecraft client) {
		if (currentPlacement != null && actions.carriedItemMatches(client, currentPlacement.ingredient().itemName())) {
			transitionCraft(State.PLACE_INGREDIENT, client);
			return;
		}
		timeout("Picked item did not reach cursor.");
	}

	private void placeIngredient(Minecraft client) {
		if (currentPlacement.count() >= 64) {
			actions.clickSlot(client, currentPlacement.slot());
		} else {
			actions.rightClickSlot(client, currentPlacement.slot());
		}
		transitionCraft(State.VERIFY_PLACEMENT, client);
	}

	private void verifyPlacement(Minecraft client) {
		if (!actions.slotContainsAtLeast(client, currentPlacement.slot(), currentPlacement.ingredient().itemName(), currentPlacement.count())) {
			timeout("Craft slot " + currentPlacement.slot() + " did not receive " + currentPlacement.count() + "x " + currentPlacement.ingredient().itemName() + ".");
			return;
		}
		if (actions.carriedItemEmpty(client)) {
			placementIndex++;
			transitionCraft(State.PICK_INGREDIENT, client);
			return;
		}
		ArmorPiece piece = craftOrder.get(pieceIndex);
		FinalDestinationCraftingPlan.Recipe recipe = FinalDestinationCraftingPlan.recipe(piece);
		if (canReuseCarriedForNextPlacement(recipe, placementIndex)) {
			placementIndex++;
			currentPlacement = recipe.placements().get(placementIndex);
			transitionCraft(State.PLACE_INGREDIENT, client);
			return;
		}
		transitionCraft(State.RETURN_CARRIED, client);
	}

	private void returnCarriedIngredient(Minecraft client) {
		actions.clickSlot(client, currentSourceSlot);
		transitionCraft(State.VERIFY_RETURNED, client);
	}

	private void verifyReturnedIngredient(Minecraft client) {
		if (actions.carriedItemEmpty(client)) {
			placementIndex++;
			transitionCraft(State.PICK_INGREDIENT, client);
			return;
		}
		timeout("Cursor still holds " + currentPlacement.ingredient().itemName() + " after returning remainder to inventory.");
	}

	private void verifyRecipe(Minecraft client) {
		ArmorPiece piece = craftOrder.get(pieceIndex);
		for (FinalDestinationCraftingPlan.Placement placement : FinalDestinationCraftingPlan.recipe(piece).placements()) {
			if (!actions.slotContainsAtLeast(client, placement.slot(), placement.ingredient().itemName(), placement.count())) {
				timeout(piece.baseName() + " recipe is incomplete at craft slot " + placement.slot() + ".");
				return;
			}
		}
		transitionCraft(State.TAKE_OUTPUT, client);
	}

	private void takeOutput(Minecraft client) {
		ArmorPiece piece = craftOrder.get(pieceIndex);
		Optional<Integer> emptySlot = actions.findEmptyInventoryHandlerSlot(client);
		if (emptySlot.isEmpty()) {
			fail("No empty inventory slot is available for " + piece.baseName() + ".");
			return;
		}
		currentOutputSlot = emptySlot.get();
		outputCountBeforeTake = actions.countInventoryItemsByName(client, piece.baseName());
		actions.clickSlot(client, FinalDestinationCraftingPlan.OUTPUT_SLOT);
		transitionCraft(State.VERIFY_OUTPUT_TAKEN, client);
	}

	private void verifyOutputTaken(Minecraft client) {
		ArmorPiece piece = craftOrder.get(pieceIndex);
		if (actions.carriedItemMatches(client, piece.baseName())) {
			transitionCraft(State.PLACE_OUTPUT, client);
			return;
		}
		if (actions.countInventoryItemsByName(client, piece.baseName()) > outputCountBeforeTake) {
			finishPiece(client);
			return;
		}
		timeout(piece.baseName() + " output did not appear after clicking craft result.");
	}

	private void placeOutput(Minecraft client) {
		actions.clickSlot(client, currentOutputSlot);
		transitionCraft(State.VERIFY_OUTPUT_PLACED, client);
	}

	private void verifyOutputPlaced(Minecraft client) {
		ArmorPiece piece = craftOrder.get(pieceIndex);
		if (actions.carriedItemEmpty(client) && actions.slotContainsAtLeast(client, currentOutputSlot, piece.baseName(), 1)) {
			finishPiece(client);
			return;
		}
		timeout(piece.baseName() + " did not settle into inventory slot " + currentOutputSlot + ".");
	}

	private void finishPiece(Minecraft client) {
		feedback.accept("AutoAuction FD crafter made " + craftOrder.get(pieceIndex).baseName() + ".");
		pieceIndex++;
		currentPlacement = null;
		currentSourceSlot = -1;
		currentOutputSlot = -1;
		transitionCraft(State.START_PIECE, client);
	}

	private void transition(State next, Minecraft client) {
		transition(next, client, clickDelayMs);
	}

	private void transition(State next, Minecraft client, int delayMs) {
		state = next;
		stateStartedAt = System.currentTimeMillis();
		nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
	}

	private void transitionCraft(State next, Minecraft client) {
		transition(next, client, CRAFT_MOVE_DELAY_MS);
	}

	private void timeout(String message) {
		if (System.currentTimeMillis() - stateStartedAt > screenTimeoutMs) {
			fail(message);
		}
	}

	private void done() {
		state = State.DONE;
		feedback.accept("AutoAuction FD crafter finished the full Final Destination set.");
		finishHandler.run();
	}

	private void fail(String message) {
		state = State.ERROR;
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			actions.closeScreen(client);
		}
		errorHandler.accept(message);
	}

	private enum State {
		CHECK_MATERIALS,
		BUY_NULL_OVOID,
		BUY_REDSTONE,
		OPEN_CRAFT,
		WAIT_CRAFT,
		START_PIECE,
		PICK_INGREDIENT,
		VERIFY_PICKED_INGREDIENT,
		PLACE_INGREDIENT,
		VERIFY_PLACEMENT,
		RETURN_CARRIED,
		VERIFY_RETURNED,
		VERIFY_RECIPE,
		TAKE_OUTPUT,
		VERIFY_OUTPUT_TAKEN,
		PLACE_OUTPUT,
		VERIFY_OUTPUT_PLACED,
		DONE,
		ERROR
	}

	private final class CraftInstantBuyWorkflow {
		private final String itemName;
		private final int quantity;
		private final int requiredInventoryCount;
		private BuyState state = BuyState.OPEN_BAZAAR;
		private int confirmClicks;
		private long nextActionAt;
		private long stateStartedAt;
		private boolean done;
		private boolean failed;
		private String errorMessage = "";

		private CraftInstantBuyWorkflow(String itemName, int quantity, int requiredInventoryCount) {
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
			this.requiredInventoryCount = Math.max(1, requiredInventoryCount);
		}

		private void start(Minecraft client) {
			feedback.accept("AutoAuction FD crafter buying " + quantity + "x " + itemName + ".");
			transition(BuyState.OPEN_BAZAAR, client, MIN_CLICK_DELAY_MS);
		}

		private void tick(Minecraft client) {
			if (done || failed || System.currentTimeMillis() < nextActionAt) {
				return;
			}
			switch (state) {
				case OPEN_BAZAAR -> {
					actions.closeScreen(client);
					actions.sendChatCommand(client, "/bz " + itemName);
					transition(BuyState.WAIT_ITEM_PAGE, client);
				}
				case WAIT_ITEM_PAGE -> {
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isBazaarResultScreen(title)) {
						Optional<Integer> itemSlot = actions.findHandlerSlotByExactItemName(client, itemName);
						if (itemSlot.isPresent()) {
							actions.clickSlot(client, itemSlot.get());
							transition(BuyState.WAIT_ITEM_PAGE, client);
							return;
						}
					}
					if (BazaarTransferWorkflow.isItemPage(title, itemName)) {
						transition(BuyState.CLICK_BUY_INSTANTLY, client);
						return;
					}
					timeout("Bazaar item page did not open for " + itemName + ".");
				}
				case CLICK_BUY_INSTANTLY -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Buy Instantly")
						.orElse(BazaarTransferWorkflow.BUY_INSTANTLY_SLOT);
					actions.clickSlot(client, slot);
					transition(BuyState.WAIT_AMOUNT_SCREEN, client);
				}
				case WAIT_AMOUNT_SCREEN -> {
					if (BazaarTransferWorkflow.isInstantBuyAmountScreen(screenTitle(client))) {
						transition(BuyState.CLICK_CUSTOM_AMOUNT, client);
						return;
					}
					timeout("Instant-buy amount screen did not open for " + itemName + ".");
				}
				case CLICK_CUSTOM_AMOUNT -> {
					int slot = actions.findHandlerSlotByExactItemName(client, "Custom Amount")
						.orElse(BazaarTransferWorkflow.CUSTOM_AMOUNT_SLOT);
					actions.clickSlot(client, slot);
					transition(BuyState.WAIT_AMOUNT_SIGN, client);
				}
				case WAIT_AMOUNT_SIGN -> {
					if (BazaarTransferWorkflow.isSignScreen(screenTitle(client))) {
						transition(BuyState.SUBMIT_AMOUNT, client);
						return;
					}
					timeout("Custom amount sign did not open for " + itemName + ".");
				}
				case SUBMIT_AMOUNT -> {
					if (!actions.submitSignText(client, BazaarTransferWorkflow.quantityText(quantity))) {
						failBuy("Could not submit instant-buy amount for " + itemName + ".");
						return;
					}
					transition(BuyState.WAIT_CONFIRM_INSTANT_BUY, client);
				}
				case WAIT_CONFIRM_INSTANT_BUY -> {
					if (BazaarTransferWorkflow.isConfirmInstantBuyScreen(screenTitle(client))) {
						transition(BuyState.CLICK_CONFIRM_INSTANT_BUY, client);
						return;
					}
					timeout("Confirm Instant Buy screen did not open for " + itemName + ".");
				}
				case CLICK_CONFIRM_INSTANT_BUY -> {
					actions.clickSlot(client, BazaarTransferWorkflow.CONFIRM_INSTANT_BUY_SLOT);
					confirmClicks++;
					transition(BuyState.WAIT_BUY_COMPLETE, client);
				}
				case CLICK_BUY_WARNING_CONFIRM -> {
					if (!BazaarTransferWorkflow.isInstantSellWarningScreen(screenTitle(client))) {
						timeout("Instant-buy warning screen disappeared before confirm for " + itemName + ".");
						return;
					}
					int slot = actions.findHandlerSlotByExactItemName(client, "WARNING")
						.orElse(BazaarTransferWorkflow.INSTANT_SELL_WARNING_SLOT);
					actions.clickSlot(client, slot);
					transition(BuyState.WAIT_BUY_COMPLETE, client);
				}
				case WAIT_BUY_COMPLETE -> {
					if (actions.countInventoryItemsByName(client, itemName) >= requiredInventoryCount) {
						actions.closeScreen(client);
						done = true;
						feedback.accept("AutoAuction FD crafter has required " + itemName + ".");
						return;
					}
					String title = screenTitle(client);
					if (BazaarTransferWorkflow.isInstantSellWarningScreen(title)) {
						transition(BuyState.CLICK_BUY_WARNING_CONFIRM, client, WARNING_CONFIRM_DELAY_MS);
						return;
					}
					if (BazaarTransferWorkflow.isConfirmInstantBuyScreen(title)
						&& confirmClicks < INSTANT_BUY_CONFIRM_MAX_CLICKS
						&& System.currentTimeMillis() - stateStartedAt > INSTANT_BUY_CONFIRM_RETRY_DELAY_MS) {
						transition(BuyState.CLICK_CONFIRM_INSTANT_BUY, client);
						return;
					}
					timeout("Inventory did not receive required " + itemName + " after instant buy.");
				}
			}
		}

		private void transition(BuyState next, Minecraft client) {
			transition(next, client, clickDelayMs);
		}

		private void transition(BuyState next, Minecraft client, int delayMs) {
			state = next;
			stateStartedAt = System.currentTimeMillis();
			nextActionAt = stateStartedAt + Math.max(MIN_CLICK_DELAY_MS, delayMs);
		}

		private void timeout(String message) {
			if (System.currentTimeMillis() - stateStartedAt > screenTimeoutMs) {
				failBuy(message);
			}
		}

		private void failBuy(String message) {
			failed = true;
			errorMessage = message;
		}

		private boolean done() {
			return done;
		}

		private boolean failed() {
			return failed;
		}

		private String errorMessage() {
			return errorMessage;
		}
	}

	private enum BuyState {
		OPEN_BAZAAR,
		WAIT_ITEM_PAGE,
		CLICK_BUY_INSTANTLY,
		WAIT_AMOUNT_SCREEN,
		CLICK_CUSTOM_AMOUNT,
		WAIT_AMOUNT_SIGN,
		SUBMIT_AMOUNT,
		WAIT_CONFIRM_INSTANT_BUY,
		CLICK_CONFIRM_INSTANT_BUY,
		CLICK_BUY_WARNING_CONFIRM,
		WAIT_BUY_COMPLETE
	}

	private String screenTitle(Minecraft client) {
		return client.screen == null ? "" : client.screen.getTitle().getString();
	}
}
