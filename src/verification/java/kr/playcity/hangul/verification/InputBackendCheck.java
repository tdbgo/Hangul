package kr.playcity.hangul.verification;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.input.KeyEvent;

/** Checks physical-key translation against the selected game's actual input backend. */
final class InputBackendCheck {
	static void run() throws Exception {
		Class<?> compat = Class.forName("kr.playcity.hangul.InputKeyCompat");
		String[][] constants = {
			{"ACTION_PRESS", "PRESS"}, {"KEY_BACKSPACE", "KEY_BACKSPACE"},
			{"KEY_CAPS_LOCK", "KEY_CAPSLOCK"}, {"KEY_F6", "KEY_F6"},
			{"KEY_LEFT_SHIFT", "KEY_LSHIFT"}, {"KEY_RIGHT_SHIFT", "KEY_RSHIFT"},
			{"MOD_SUPER", "MOD_SUPER"}
		};
		for (String[] pair : constants) {
			Field field = compat.getDeclaredField(pair[0]);
			field.setAccessible(true);
			check(field.getInt(null) == key(pair[1]), "backend constant " + pair[0]);
		}
		Method letter = method(compat, "latinLetter", KeyEvent.class);
		Class<?> controller = Class.forName("kr.playcity.hangul.HangulController");
		Method plainLetter = method(controller, "plainLatinLetter", KeyEvent.class);
		Method toggle = method(controller, "isToggleKey", int.class);
		Method dispatch = controller.getMethod("onKeyPress", long.class, int.class, KeyEvent.class);
		for (char ch = 'A'; ch <= 'Z'; ch++) {
			int physical = key("KEY_" + ch);
			// The second field must not override the physical key with a layout-dependent code.
			KeyEvent event = new KeyEvent(physical, 12345, 0);
			check(letter.invoke(null, event).equals((int) ch), "physical letter " + ch);
			check(plainLetter.invoke(null, event).equals((int) ch), "plain letter " + ch);
			check(!(boolean) dispatch.invoke(null, 0L, key("PRESS"), event), "native letter passthrough " + ch);
			for (String modifier : new String[]{"MOD_CONTROL", "MOD_ALT", "MOD_SUPER"}) {
				check(plainLetter.invoke(null, new KeyEvent(physical, 0, key(modifier))).equals(-1),
					"shortcut is not composed " + modifier + "/" + ch);
			}
			KeyEvent shifted = new KeyEvent(physical, 0, key("MOD_SHIFT"));
			check(shifted.hasShiftDown() && plainLetter.invoke(null, shifted).equals((int) ch),
				"shift preserves physical letter " + ch);
		}
		for (String name : new String[]{"KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_SPACE",
				"KEY_RETURN", "KEY_BACKSPACE", "KEY_LALT", "KEY_RALT", "KEY_RCONTROL"}) {
			KeyEvent event = new KeyEvent(key(name), 0, 0);
			check(letter.invoke(null, event).equals(-1), "non-letter " + name);
			for (String action : new String[]{"PRESS", "RELEASE", "REPEAT"}) {
				check(!(boolean) dispatch.invoke(null, 0L, key(action), event), "native passthrough " + name + "/" + action);
			}
		}
		check((boolean) toggle.invoke(null, key("KEY_F6")), "backend F6 toggle");
		check(!(boolean) toggle.invoke(null, key("KEY_RALT")), "language switch is not a toggle");
		System.out.println("INPUT_BACKEND_OK physical A-Z, Shift, shortcuts, F6 mapping, and native key passthrough");
	}

	private static int key(String name) throws Exception {
		return InputConstants.class.getField(name).getInt(null);
	}

	private static Method method(Class<?> type, String name, Class<?> parameter) throws Exception {
		Method method = type.getDeclaredMethod(name, parameter);
		method.setAccessible(true);
		return method;
	}

	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}
}
