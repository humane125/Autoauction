package com.autoauction.client.macro;

public final class NebulaGuiInputTracker {
	private boolean maybeOpen;
	private boolean guiKeyWasDown;
	private boolean escapeWasDown;
	private boolean minecraftScreenWasOpen;

	public boolean tick(boolean minecraftScreenOpen, boolean guiKeyDown, boolean escapeDown) {
		boolean opened = false;
		if (escapeDown && !escapeWasDown) {
			maybeOpen = false;
		} else if (!minecraftScreenWasOpen && guiKeyDown && !guiKeyWasDown) {
			maybeOpen = true;
			opened = true;
		}
		guiKeyWasDown = guiKeyDown;
		escapeWasDown = escapeDown;
		minecraftScreenWasOpen = minecraftScreenOpen;
		return opened;
	}

	public boolean blocksMacroIntent(boolean minecraftScreenOpen) {
		return minecraftScreenOpen || maybeOpen;
	}

	public boolean maybeOpen() {
		return maybeOpen;
	}
}
