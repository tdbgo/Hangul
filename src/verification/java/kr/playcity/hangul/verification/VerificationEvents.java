package kr.playcity.hangul.verification;

import java.lang.reflect.Constructor;
import net.minecraft.client.input.KeyEvent;

/** Constructs real runtime events across the addition of the key-repeat flag. */
final class VerificationEvents {
	private static final Constructor<KeyEvent> KEY = keyConstructor();

	static KeyEvent key(final int key, final int scancode, final int modifiers) throws Exception {
		return KEY.getParameterCount() == 3 ? KEY.newInstance(key, scancode, modifiers)
			: KEY.newInstance(key, scancode, modifiers, false);
	}

	private static Constructor<KeyEvent> keyConstructor() {
		try {
			try { return KeyEvent.class.getConstructor(int.class, int.class, int.class, boolean.class); }
			catch (NoSuchMethodException legacy) { return KeyEvent.class.getConstructor(int.class, int.class, int.class); }
		} catch (NoSuchMethodException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}
}
