package kr.playcity.hangul.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks transformed sign methods, not OS input delivery, rendering pixels, or world persistence. */
final class SignBehaviorCheck {
	static void run(Font font) throws Exception {
		// Skip the screen constructor: it starts native text input and requires a live world.
		// Only the state used by the methods below is installed. This fixture is never shipped.
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field instance = unsafeType.getDeclaredField("theUnsafe");
		instance.setAccessible(true);
		AbstractSignEditScreen screen = (AbstractSignEditScreen) unsafeType.getMethod("allocateInstance", Class.class)
			.invoke(instance.get(null), SignEditScreen.class);
		if (Arrays.stream(AbstractSignEditScreen.class.getDeclaredFields()).anyMatch(field -> field.getName().equals("signLines"))) {
			checkModern(screen, font);
			return;
		}
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

	private static void checkModern(AbstractSignEditScreen screen, Font font) throws Exception {
		Field fontField = Screen.class.getDeclaredField("font");
		fontField.setAccessible(true);
		fontField.set(screen, font);
		Class<?> lineType = Class.forName(AbstractSignEditScreen.class.getName() + "$SignLineEditBox");
		check(lineType.getSuperclass() == EditBox.class, "modern sign lines inherit EditBox");
		var constructor = lineType.getDeclaredConstructor(AbstractSignEditScreen.class, int.class, int.class);
		constructor.setAccessible(true);
		String[] messages = {"abcd", "둘", "셋", "넷"};
		member("messages").set(screen, messages);
		EditBox line = (EditBox) constructor.newInstance(screen, 120, 10);
		line.setValue(messages[0]);
		int[] writes = {0};
		line.setResponder(value -> { messages[0] = value; writes[0]++; });
		WidgetBehaviorCheck.setField(line, "focused", true);
		line.moveCursorTo(1, false);
		line.moveCursorTo(3, true);
		line.preeditUpdated(WidgetBehaviorCheck.preedit("한", 1));
		check(writes[0] == 0 && messages[0].equals("abcd"), "modern sign preedit does not write saved text");
		RuntimeException sentinel = new RuntimeException("intentional modern sign render interruption");
		try {
			WidgetBehaviorCheck.invoke(WidgetBehaviorCheck.wrapper(EditBox.class), line, args -> {
				check(line.getValue().equals("a한d"), "modern sign inline selection");
				throw sentinel;
			});
			throw new AssertionError("Expected modern sign render interruption");
		} catch (InvocationTargetException failure) {
			if (failure.getCause() != sentinel) throw failure;
		}
		check(line.getValue().equals("abcd") && line.getCursorPosition() == 3, "modern sign render restores body and cursor");
		line.charTyped(new CharacterEvent('漢'));
		check(messages[0].equals("a漢d") && writes[0] == 1, "modern sign Hanja commit writes exactly once");
		line.preeditUpdated(WidgetBehaviorCheck.preedit("나", 1));
		Method focus = Arrays.stream(EditBox.class.getDeclaredMethods())
			.filter(method -> method.getName().contains("hangul$onFocusChanged")).findFirst().orElseThrow();
		focus.setAccessible(true);
		focus.invoke(line, false, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("setFocused", false));
		WidgetBehaviorCheck.setField(line, "focused", false);
		check(messages[0].equals("a漢d") && messages[1].equals("둘"), "modern sign focus change preserves all saved lines");
		System.out.println("MODERN_SIGN_BEHAVIOR_OK inline composition, selection, Hanja commit, render restoration, and focus cleanup");
	}

	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}
}
