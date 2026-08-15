package kr.playcity.hangul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges the pure composer to Minecraft's focused vanilla text box. */
public final class HangulController {
	private static final int ACTION_PRESS = 1;
	private static final int KEY_BACKSPACE = 259;
	private static final int KEY_CAPS_LOCK = 280;
	private static final int KEY_F6 = 295;
	private static final int KEY_LEFT_SHIFT = 340;
	private static final int KEY_RIGHT_SHIFT = 344;

	private static final Logger LOGGER = LoggerFactory.getLogger("hangul");
	private static final HangulComposer COMPOSER = new HangulComposer();

	private static boolean forcedHangulMode;
	private static EditBox anchorBox;
	private static String anchorValue;
	private static String anchorSelection;
	private static int anchorCursor;

	private HangulController() {
	}

	public static boolean onKeyPress(final long handle, final int action, final KeyEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (handle != minecraft.getWindow().handle()) {
			return false;
		}

		Screen screen = minecraft.gui.screen();
		if (isToggleKey(event.key()) && screen != null && minecraft.gui.overlay() == null) {
			if (action == ACTION_PRESS) {
				commit();
				forcedHangulMode = !forcedHangulMode;
				EditBox box = activeBox(minecraft);
				if (box != null) {
					box.preeditUpdated(null);
				}
				Component status = Component.literal(
					forcedHangulMode ? "\uAC15\uC81C \uD55C\uAE00 [\uAC00]" : "\uC790\uB3D9 \uC785\uB825 [IME]"
				);
				minecraft.gui.hud.setOverlayMessage(status, false);
				GuiEventListener focused = screen.getFocused();
				LOGGER.info(
					"Input mode changed to {} with key {} (screen={}, focused={})",
					forcedHangulMode ? "forced Hangul" : "native IME auto",
					event.key(),
					screen.getClass().getSimpleName(),
					focused == null ? "none" : focused.getClass().getSimpleName()
				);
			}
			return true;
		}

		return false;
	}

	public static boolean onEditBoxKeyPress(final EditBox box, final KeyEvent event) {
		if (!forcedHangulMode || !box.canConsumeInput()) {
			return false;
		}

		ensureContext(box);
		if (isPlainLetter(event)) {
			HangulComposer.Edit edit = COMPOSER.inputDubeolsik(event.key(), event.hasShiftDown());
			if (edit != null) {
				apply(box, edit);
				return true;
			}
		}

		if (event.key() == KEY_BACKSPACE && COMPOSER.isComposing()) {
			HangulComposer.Edit edit = COMPOSER.backspace();
			if (edit != null) {
				apply(box, edit);
				return true;
			}
		}

		if (commitsComposition(event.key()) || event.hasControlDownWithQuirk() || event.hasAltDown()) {
			commit();
		}
		return false;
	}

	public static boolean onEditBoxCharacter(final EditBox box, final CharacterEvent event) {
		if (!forcedHangulMode || !box.canConsumeInput()) {
			return false;
		}

		int codePoint = event.codepoint();
		if (isLatinLetter(codePoint) || isHangul(codePoint)) {
			return true;
		}

		commit();
		return false;
	}

	public static boolean isForcedHangulMode() {
		return forcedHangulMode;
	}

	private static EditBox activeBox(final Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		if (screen == null || minecraft.gui.overlay() != null) {
			return null;
		}

		GuiEventListener focused = screen.getFocused();
		if (focused instanceof EditBox box && box.canConsumeInput()) {
			return box;
		}

		return null;
	}

	private static void ensureContext(final EditBox box) {
		if (!COMPOSER.isComposing()) {
			clearAnchor();
			return;
		}

		boolean unchanged = box == anchorBox
			&& box.getCursorPosition() == anchorCursor
			&& box.getValue().equals(anchorValue)
			&& box.getHighlighted().equals(anchorSelection);
		if (!unchanged) {
			commit();
		}
	}

	private static void apply(final EditBox box, final HangulComposer.Edit edit) {
		if (edit.replacePrevious()) {
			int end = box.getCursorPosition();
			if (end <= 0) {
				commit();
				return;
			}

			int start = box.getValue().offsetByCodePoints(end, -1);
			box.moveCursorTo(start, false);
			box.moveCursorTo(end, true);
		}

		box.insertText(edit.text());
		if (COMPOSER.isComposing()) {
			anchorBox = box;
			anchorValue = box.getValue();
			anchorSelection = box.getHighlighted();
			anchorCursor = box.getCursorPosition();
		} else {
			clearAnchor();
		}
	}

	private static boolean commitsComposition(final int key) {
		return !(key >= 65 && key <= 90)
			&& key != KEY_LEFT_SHIFT
			&& key != KEY_RIGHT_SHIFT
			&& key != KEY_CAPS_LOCK;
	}

	private static boolean isToggleKey(final int key) {
		return key == KEY_F6;
	}

	private static boolean isPlainLetter(final KeyEvent event) {
		return event.key() >= 65
			&& event.key() <= 90
			&& !event.hasControlDownWithQuirk()
			&& !event.hasAltDown()
			&& (event.modifiers() & 8) == 0;
	}

	private static boolean isLatinLetter(final int codePoint) {
		return codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z';
	}

	private static boolean isHangul(final int codePoint) {
		return codePoint >= 0x1100 && codePoint <= 0x11FF
			|| codePoint >= 0x3130 && codePoint <= 0x318F
			|| codePoint >= 0xA960 && codePoint <= 0xA97F
			|| codePoint >= 0xAC00 && codePoint <= 0xD7AF
			|| codePoint >= 0xD7B0 && codePoint <= 0xD7FF;
	}

	private static void commit() {
		COMPOSER.commit();
		clearAnchor();
	}

	private static void clearAnchor() {
		anchorBox = null;
		anchorValue = null;
		anchorSelection = null;
		anchorCursor = 0;
	}
}
