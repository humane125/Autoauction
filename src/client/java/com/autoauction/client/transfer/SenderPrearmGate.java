package com.autoauction.client.transfer;

public final class SenderPrearmGate {
	private boolean screenArmed;
	private boolean readySignal;

	public void markScreenArmed() {
		screenArmed = true;
	}

	public void markReadySignal() {
		readySignal = true;
	}

	public boolean canFinalClick() {
		return screenArmed && readySignal;
	}
}
