package kr.playcity.hangul;

import kr.playcity.hangul.mixin.EditBoxAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges the pure composer to Minecraft's focused vanilla text box. */
public final class HangulController {
	private static final Logger LOGGER = LoggerFactory.getLogger("hangul");
	private static final FallbackSession SESSION = new FallbackSession();

	private HangulController() {
	}

	public static boolean onKeyPress(final long handle, final int action, final KeyEvent event) {
		// Native input and movement never need reflective screen lookup.
		if (!isToggleKey(event.key())) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (handle != minecraft.getWindow().handle() || !minecraft.isWindowActive()) {
			return false;
		}

		Screen screen = MinecraftUiCompat.screen(minecraft);
		EditBox box = activeBox(minecraft, screen);
		if (box != null) {
			if (action == InputKeyCompat.ACTION_PRESS) {
				boolean forcedHangulMode = SESSION.toggle(box, screen);
				box.preeditUpdated(null);
				Component status = Component.literal(
					forcedHangulMode ? "\uAC15\uC81C \uD55C\uAE00 [\uAC00]" : "\uC790\uB3D9 \uC785\uB825 [IME]"
				);
				MinecraftUiCompat.showStatus(minecraft, status);
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
		if (!isForcedHangulMode(box)) {
			return false;
		}

		SESSION.validateAnchor(box.getValue(), box.getCursorPosition(), !box.getHighlighted().isEmpty());
		int latinLetter = plainLatinLetter(event);
		if (latinLetter >= 0) {
			HangulComposer.Edit edit = SESSION.composer().inputDubeolsik(latinLetter, event.hasShiftDown());
			if (edit != null) {
				apply(box, edit);
				return true;
			}
		}

		if (event.key() == InputKeyCompat.KEY_BACKSPACE && SESSION.composer().isComposing()
			&& !event.hasControlDownWithQuirk() && !event.hasAltDown()
			&& (event.modifiers() & InputKeyCompat.MOD_SUPER) == 0) {
			HangulComposer.Edit edit = SESSION.composer().backspace();
			if (edit != null) {
				apply(box, edit);
				return true;
			}
		}

		if (commitsComposition(event.key()) || event.hasControlDownWithQuirk() || event.hasAltDown()) {
			SESSION.commit();
		}
		return false;
	}

	public static boolean onEditBoxCharacter(final EditBox box, final CharacterEvent event) {
		if (!isForcedHangulMode(box)) {
			return false;
		}

		int codePoint = event.codepoint();
		if (isLatinLetter(codePoint) || isHangul(codePoint)) {
			return true;
		}

		SESSION.commit();
		return false;
	}

	public static boolean isForcedHangulMode(final EditBox box) {
		if (!SESSION.owns(box)) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = MinecraftUiCompat.screen(minecraft);
		if (!minecraft.isWindowActive() || !SESSION.owns(box, screen)
			|| activeBox(minecraft, screen) != box) {
			SESSION.clear();
			return false;
		}
		return true;
	}

	public static void onFocusLost(final EditBox box) {
		SESSION.release(box);
	}

	public static void onScreenChanged() {
		SESSION.clear();
	}

	private static EditBox activeBox(final Minecraft minecraft, final Screen screen) {
		if (screen == null || MinecraftUiCompat.hasOverlay(minecraft)) {
			return null;
		}

		GuiEventListener focused = screen.getFocused();
		// Text boxes in nested panels are just as eligible as direct screen children.
		for (int depth = 0; depth < 32 && focused instanceof ContainerEventHandler container; depth++) {
			GuiEventListener child = container.getFocused();
			if (child == focused) {
				return null;
			}
			focused = child;
		}
		if (focused instanceof EditBox box && box.canConsumeInput()) {
			return box;
		}

		return null;
	}

	private static void apply(final EditBox box, final HangulComposer.Edit edit) {
		if (edit.replacePrevious()) {
			int end = box.getCursorPosition();
			if (end <= 0) {
				SESSION.commit();
				return;
			}

			int start = box.getValue().offsetByCodePoints(end, -1);
			box.moveCursorTo(start, false);
			box.moveCursorTo(end, true);
		}

		String value = box.getValue();
		int cursor = box.getCursorPosition();
		int selection = ((EditBoxAccessor) box).hangul$getHighlightPos();
		int start = Math.min(cursor, selection);
		int end = Math.max(cursor, selection);
		String expected = value.substring(0, start) + edit.text() + value.substring(end);
		box.insertText(edit.text());
		SESSION.acceptEdit(expected, start + edit.text().length(), box.getValue(),
			box.getCursorPosition(), !box.getHighlighted().isEmpty());
	}

	private static boolean commitsComposition(final int key) {
		return InputKeyCompat.latinLetter(key) < 0
			&& key != InputKeyCompat.KEY_LEFT_SHIFT
			&& key != InputKeyCompat.KEY_RIGHT_SHIFT
			&& key != InputKeyCompat.KEY_CAPS_LOCK;
	}

	private static boolean isToggleKey(final int key) {
		return key == InputKeyCompat.KEY_F6;
	}

	private static int plainLatinLetter(final KeyEvent event) {
		if (!event.hasControlDownWithQuirk()
			&& !event.hasAltDown()
			&& (event.modifiers() & InputKeyCompat.MOD_SUPER) == 0) {
			return InputKeyCompat.latinLetter(event);
		}
		return -1;
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

}
