package kr.playcity.hangul;

import net.minecraft.client.Minecraft;

/** Positions the operating-system IME candidate window at the active text caret. */
public final class NativeImeSupport {
	private NativeImeSupport() {
	}

	public static void positionCandidateWindow(final int x, final int y, final int height) {
		int safeHeight = Math.max(1, height);
		Minecraft.getInstance().textInputManager().setTextInputArea(x, y, x + 1, y + safeHeight);
	}
}
