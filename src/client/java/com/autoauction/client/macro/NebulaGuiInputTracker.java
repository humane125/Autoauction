package com.autoauction.client.macro;

public final class NebulaGuiInputTracker {
	private boolean maybeOpen;
	private boolean guiKeyWasDown;
	private boolean escapeWasDown;

	public void tick(boolean minecraftScreenOpen, boolean guiKeyDown, boolean escapeDown) {
		if (escapeDown && !escapeWasDown) {
			maybeOpen = false;
		} else if (!minecraftScreenOpen && guiKeyDown && !guiKeyWasDown) {
			maybeOpen = true;
		}
		guiKeyWasDown = guiKeyDown;
		escapeWasDown = escapeDown;
	}

	public boolean blocksMacroIntent(boolean minecraftScreenOpen) {
		return minecraftScreenOpen || maybeOpen;
	}

	public boolean maybeOpen() {
		return maybeOpen;
	}
}
