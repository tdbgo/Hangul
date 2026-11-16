package kr.playcity.hangul;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.gui.components.EditBox;

/** Distinguishes keyboard selection from text editing on newer input widgets. */
public final class EditBoxInputCompat {
	private static final MethodHandle CAPTURES_INPUT = capturesInput();

	private EditBoxInputCompat() {}

	public static boolean acceptsText(final EditBox box) {
		if (!box.isVisible() || !box.canConsumeInput()) return false;
		if (CAPTURES_INPUT == null) return true;
		try {
			return (boolean) CAPTURES_INPUT.invokeExact(box);
		} catch (Throwable failure) {
			throw new IllegalStateException("Could not read the text widget's editing state", failure);
		}
	}

	private static MethodHandle capturesInput() {
		try {
			return MethodHandles.publicLookup().findVirtual(EditBox.class, "capturesInput", MethodType.methodType(boolean.class));
		} catch (NoSuchMethodException legacy) {
			// Earlier widgets have no separate highlighted-but-not-editing state.
			return null;
		} catch (IllegalAccessException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}
}
