package kr.playcity.hangul.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks transformed sign methods, not OS input delivery, rendering pixels, or world persistence. */
final class SignBehaviorCheck {
	static void run() throws Exception {
		// Skip the screen constructor: it starts native text input and requires a live world.
		// Only the state used by the methods below is installed. This fixture is never shipped.
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field instance = unsafeType.getDeclaredField("theUnsafe");
		instance.setAccessible(true);
		AbstractSignEditScreen screen = (AbstractSignEditScreen) unsafeType.getMethod("allocateInstance", Class.class)
			.invoke(instance.get(null), SignEditScreen.class);
		String[] messages = {"abcd", "둘", "셋", "넷"};
		Field line = member("line");
		member("messages").set(screen, messages);
		member("hangul$visualCache").set(screen, Class.forName("kr.playcity.hangul.PreeditCache").getConstructor().newInstance());
		member("hangul$preeditLine").setInt(screen, -1);
		TextFieldHelper helper = new TextFieldHelper(
			() -> messages[currentLine(line, screen)], value -> messages[currentLine(line, screen)] = value,
			() -> "", value -> {}, value -> value.length() <= 90);
		member("signField").set(screen, helper);
		helper.setSelectionRange(1, 3);
		Method preedit = method("hangul$onPreedit", 2);
		Method render = method("hangul$renderInlinePreedit", 5);
		update(preedit, screen, "한");
		Operation<Void> visual = args -> {
			check(messages[0].equals("a한d"), "inline sign selection");
			check(helper.getCursorPos() == 2 && helper.getSelectionPos() == 2, "inline sign caret");
			return null;
		};
		render.invoke(screen, null, 0, 0, 0f, visual);
		check(messages[0].equals("abcd") && helper.getCursorPos() == 1 && helper.getSelectionPos() == 3,
			"sign render restores original body and selection");
		RuntimeException sentinel = new RuntimeException("intentional sign render interruption");
		try {
			render.invoke(screen, null, 0, 0, 0f, (Operation<Void>) args -> { throw sentinel; });
			throw new AssertionError("Expected render interruption");
		} catch (InvocationTargetException failure) {
			if (failure.getCause() != sentinel) throw failure;
		}
		check(messages[0].equals("abcd") && helper.getCursorPos() == 1 && helper.getSelectionPos() == 3,
			"sign exception restores original body and selection");
		screen.charTyped(new CharacterEvent('漢'));
		check(messages[0].equals("a漢d") && member("hangul$preedit").get(screen) == null,
			"sign conversion commit replaces selection once");
		update(preedit, screen, "ㄱ");
		check(screen.keyPressed(key("KEY_DOWN")) && line.getInt(screen) == 1, "Down advances sign line");
		render.invoke(screen, null, 0, 0, 0f, (Operation<Void>) args -> {
			check(messages[1].equals("둘"), "old composition does not leak to next line");
			return null;
		});
		check(member("hangul$preedit").get(screen) == null, "line change clears stale preedit");
		check(screen.keyPressed(key("KEY_UP")) && line.getInt(screen) == 0, "Up returns to sign line");
		check(screen.keyPressed(key("KEY_UP")) && line.getInt(screen) == 3, "Up wraps sign line");
		check(screen.keyPressed(key("KEY_RETURN")) && line.getInt(screen) == 0, "Enter wraps sign line");
		update(preedit, screen, "나");
		screen.preeditUpdated(null);
		check(member("hangul$preedit").get(screen) == null && messages[0].equals("a漢d"), "sign cancellation preserves text");
		System.out.println("SIGN_BEHAVIOR_OK selection, conversion events, restoration, Up/Down/Enter, and cancellation");
	}

	private static int currentLine(Field line, Object screen) {
		try { return line.getInt(screen); }
		catch (IllegalAccessException failure) { throw new AssertionError(failure); }
	}

	private static KeyEvent key(String name) throws Exception {
		return new KeyEvent(com.mojang.blaze3d.platform.InputConstants.class.getField(name).getInt(null), 0, 0);
	}

	private static Field member(String name) throws Exception {
		Field field = AbstractSignEditScreen.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Method method(String name, int count) {
		Method method = Arrays.stream(AbstractSignEditScreen.class.getDeclaredMethods())
			.filter(item -> item.getName().contains(name) && item.getParameterCount() == count).findFirst().orElseThrow();
		method.setAccessible(true);
		return method;
	}

	private static void update(Method method, Object screen, String text) throws Exception {
		method.invoke(screen, new PreeditEvent(text, 1, List.of(text), 0), new CallbackInfoReturnable<Boolean>("preeditUpdated", false));
	}

	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}
}
