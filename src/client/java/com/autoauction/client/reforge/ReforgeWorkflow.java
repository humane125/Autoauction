package com.autoauction.client.reforge;

import com.autoauction.client.minecraft.MinecraftGameActions;
import net.minecraft.client.Minecraft;

import java.util.Optional;
import java.util.function.Consumer;

public final class ReforgeWorkflow {
	private static final int MAX_REFORGE_ATTEMPTS_PER_ITEM = 80;

	private final ReforgeTargetPlan.Plan plan;
	private final MinecraftGameActions actions;
	private final int screenTimeoutMs;
	private final Consumer<String> feedback;
	private final Consumer<String> errorHandler;
	private final Runnable finishHandler;

	private State state = State.FIND_NEXT_ITEM;
	private int baseNameIndex;
	private int sourceSlot = -1;
	private String currentBaseName = "";
	private int attemptsForCurrentItem;
	private long stateStartedAt;
	private long nextActionAt;

	public ReforgeWorkflow(
		ReforgeTargetPlan.Plan plan,
		MinecraftGameActions actions,
		int screenTimeoutMs,
		Consumer<String> feedback,
		Consumer<String> errorHandler,
		Runnable finishHandler
	) {
		this.plan = plan;
		this.actions = actions;
		this.screenTimeoutMs = screenTimeoutMs;
		this.feedback = feedback;
		this.errorHandler = errorHandler;
		this.finishHandler = finishHandler;
	}

	public void start(Minecraft client) {
		feedback.accept("AutoAuction reforge started for " + plan.name() + " -> " + plan.reforge() + ".");
		transition(State.FIND_NEXT_ITEM, client);
	}

	public void tick(Minecraft client) {
		if (state == State.DONE || state == State.ERROR || System.currentTimeMillis() < nextActionAt) {
			return;
		}
		try {
			runState(client);
		} catch (Exception e) {
			fail("reforge workflow crashed: " + e.getMessage());
		}
	}

	private void runState(Minecraft client) {
		if (!actions.screenTitleContains(client, "Reforge Item")) {
			fail("Reforge Item menu is not open.");
			return;
		}
		switch (state) {
			case FIND_NEXT_ITEM -> findNextItem(client);
			case PICK_ITEM -> {
				actions.clickSlot(client, sourceSlot);
				transition(State.VERIFY_PICKED, client);
			}
			case VERIFY_PICKED -> {
				if (actions.carriedItemMatches(client, currentBaseName)) {
					transition(State.PLACE_INPUT, client);
					return;
				}
				timeout("Could not pick up " + currentBaseName + ".");
			}
			case PLACE_INPUT -> {
				actions.clickSlot(client, ReforgeTargetPlan.INPUT_SLOT);
				transition(State.VERIFY_INPUT, client);
			}
			case VERIFY_INPUT -> {
				if (actions.carriedItemEmpty(client)
					&& actions.slotContainsAtLeast(client, ReforgeTargetPlan.INPUT_SLOT, currentBaseName, 1)) {
					transition(State.CLICK_REFORGE, client);
					return;
				}
				timeout(currentBaseName + " did not settle into the reforge input slot.");
			}
			case CLICK_REFORGE -> clickReforge(client);
			case CHECK_REFORGE -> checkReforgeResult(client);
			case PICK_RESULT -> {
				actions.clickSlot(client, ReforgeTargetPlan.INPUT_SLOT);
				transition(State.VERIFY_RESULT_PICKED, client);
			}
			case VERIFY_RESULT_PICKED -> {
				if (actions.carriedItemMatches(client, currentBaseName)) {
					transition(State.RETURN_RESULT, client);
					return;
				}
				timeout("Could not pick up reforged " + currentBaseName + ".");
			}
			case RETURN_RESULT -> {
				actions.clickSlot(client, sourceSlot);
				transition(State.VERIFY_RETURNED, client);
			}
			case VERIFY_RETURNED -> {
				Optional<String> returnedName = actions.itemNameAt(client, sourceSlot);
				if (actions.carriedItemEmpty(client)
					&& returnedName.isPresent()
					&& ReforgeTargetPlan.itemHasTargetReforge(returnedName.get(), plan.reforge())
					&& ReforgeTargetPlan.itemMatchesPlan(returnedName.get(), plan)) {
					feedback.accept("AutoAuction reforged " + currentBaseName + " to " + plan.reforge() + ".");
					baseNameIndex++;
					sourceSlot = -1;
					currentBaseName = "";
					attemptsForCurrentItem = 0;
					transition(State.FIND_NEXT_ITEM, client);
					return;
				}
				timeout("Reforged " + currentBaseName + " did not return to inventory.");
			}
			case DONE, ERROR -> {
			}
		}
	}

	private void findNextItem(Minecraft client) {
		while (baseNameIndex < plan.baseNames().size()) {
			currentBaseName = plan.baseNames().get(baseNameIndex);
			Optional<Integer> slot = actions.findInventoryHandlerSlotByItemName(client, currentBaseName, 1);
			if (slot.isEmpty()) {
				fail("Could not find " + currentBaseName + " in your inventory.");
				return;
			}
			Optional<String> itemName = actions.itemNameAt(client, slot.get());
			if (itemName.isPresent() && ReforgeTargetPlan.itemHasTargetReforge(itemName.get(), plan.reforge())) {
				feedback.accept(currentBaseName + " already has " + plan.reforge() + "; skipping.");
				baseNameIndex++;
				continue;
			}
			sourceSlot = slot.get();
			attemptsForCurrentItem = 0;
			transition(State.PICK_ITEM, client);
			return;
		}
		done(client);
	}

	private void clickReforge(Minecraft client) {
		if (!actions.slotContainsAtLeast(client, ReforgeTargetPlan.INPUT_SLOT, currentBaseName, 1)) {
			timeout(currentBaseName + " is missing from the reforge input slot.");
			return;
		}
		if (attemptsForCurrentItem >= MAX_REFORGE_ATTEMPTS_PER_ITEM) {
			fail("Could not roll " + plan.reforge() + " on " + currentBaseName + " after " + MAX_REFORGE_ATTEMPTS_PER_ITEM + " attempts.");
			return;
		}
		attemptsForCurrentItem++;
		actions.clickSlot(client, ReforgeTargetPlan.REFORGE_BUTTON_SLOT);
		transition(State.CHECK_REFORGE, client, 450);
	}

	private void checkReforgeResult(Minecraft client) {
		Optional<String> itemName = actions.itemNameAt(client, ReforgeTargetPlan.INPUT_SLOT);
		if (itemName.isEmpty()) {
			timeout("Reforge input slot became empty for " + currentBaseName + ".");
			return;
		}
		if (ReforgeTargetPlan.itemHasTargetReforge(itemName.get(), plan.reforge())) {
			transition(State.PICK_RESULT, client);
			return;
		}
		transition(State.CLICK_REFORGE, client);
	}

	private void transition(State next, Minecraft client) {
		transition(next, client, ReforgeTargetPlan.MOVE_DELAY_MS);
	}

	private void transition(State next, Minecraft client, int delayMs) {
		state = next;
		stateStartedAt = System.currentTimeMillis();
		nextActionAt = stateStartedAt + Math.max(100, delayMs);
	}

	private void timeout(String message) {
		if (System.currentTimeMillis() - stateStartedAt > screenTimeoutMs) {
			fail(message);
		}
	}

	private void done(Minecraft client) {
		state = State.DONE;
		actions.closeScreen(client);
		feedback.accept("AutoAuction reforge finished for " + plan.name() + " -> " + plan.reforge() + ".");
		finishHandler.run();
	}

	private void fail(String message) {
		state = State.ERROR;
		errorHandler.accept(message);
		finishHandler.run();
	}

	private enum State {
		FIND_NEXT_ITEM,
		PICK_ITEM,
		VERIFY_PICKED,
		PLACE_INPUT,
		VERIFY_INPUT,
		CLICK_REFORGE,
		CHECK_REFORGE,
		PICK_RESULT,
		VERIFY_RESULT_PICKED,
		RETURN_RESULT,
		VERIFY_RETURNED,
		DONE,
		ERROR
	}
}
