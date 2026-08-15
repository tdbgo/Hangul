package kr.playcity.hangul;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/** Resolves physical-key constants across the GLFW and SDL input backends. */
final class InputKeyCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("hangul");

	static final int ACTION_PRESS = constant("PRESS", 1);
	static final int KEY_BACKSPACE = constant("KEY_BACKSPACE", 259);
	static final int KEY_CAPS_LOCK = constant("KEY_CAPSLOCK", 280);
	static final int KEY_F6 = constant("KEY_F6", 295);
	static final int KEY_LEFT_SHIFT = constant("KEY_LSHIFT", 340);
	static final int KEY_RIGHT_SHIFT = constant("KEY_RSHIFT", 344);
	static final int MOD_SUPER = constant("MOD_SUPER", 8);

	private static final int[] LATIN_KEYS = {
		constant("KEY_A", 65), constant("KEY_B", 66), constant("KEY_C", 67),
		constant("KEY_D", 68), constant("KEY_E", 69), constant("KEY_F", 70),
		constant("KEY_G", 71), constant("KEY_H", 72), constant("KEY_I", 73),
		constant("KEY_J", 74), constant("KEY_K", 75), constant("KEY_L", 76),
		constant("KEY_M", 77), constant("KEY_N", 78), constant("KEY_O", 79),
		constant("KEY_P", 80), constant("KEY_Q", 81), constant("KEY_R", 82),
		constant("KEY_S", 83), constant("KEY_T", 84), constant("KEY_U", 85),
		constant("KEY_V", 86), constant("KEY_W", 87), constant("KEY_X", 88),
		constant("KEY_Y", 89), constant("KEY_Z", 90)
	};

	private InputKeyCompat() {
	}

	/** Returns the corresponding ASCII letter for a physical A-Z key, or {@code -1}. */
	static int latinLetter(final KeyEvent event) {
		return latinLetter(event.key());
	}

	static int latinLetter(final int physicalKey) {
		for (int index = 0; index < LATIN_KEYS.length; index++) {
			if (physicalKey == LATIN_KEYS[index]) {
				return 'A' + index;
			}
		}
		return -1;
	}

	private static int constant(final String name, final int fallback) {
		try {
			Field field = InputConstants.class.getField(name);
			return field.getInt(null);
		} catch (ReflectiveOperationException | LinkageError exception) {
			LOGGER.warn("Input constant {} is unavailable; using the legacy fallback", name);
			return fallback;
		}
	}
}
