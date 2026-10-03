package kr.playcity.hangul.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.PreeditEvent;
import sun.misc.Unsafe;

/** Real optional-mod classes transformed by the loader; no window or OS IME simulation. */
final class OptionalWidgetBehaviorCheck {
	static void run() throws Exception {
		Class<?> type = Class.forName("me.shedaniel.rei.impl.client.gui.widget.basewidgets.TextFieldWidget");
		boolean adapted = Arrays.stream(type.getInterfaces()).anyMatch(i -> i.getName().equals("kr.playcity.hangul.PreeditValueProvider"));
		if (Boolean.getBoolean("hangul.verification.imblocker")) {
			Class.forName("io.github.reserveword.imblocker.common.IMBlockerMixinPlugin", false, type.getClassLoader());
			check(!adapted, "IMBlocker profile must not apply two optional-widget adapters");
			check(Arrays.stream(type.getInterfaces()).anyMatch(i -> i.getName().contains("MinecraftTextFieldWidget")),
				"IMBlocker REI adapter actually applied");
			System.out.println("OPTIONAL_WIDGET_OK rei=true imblocker=true owner=IMBlocker (native IME not exercised)");
			return;
		}
		check(adapted, "REI adapter actually applied");
		Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Unsafe unsafe = (Unsafe) unsafeField.get(null);
		// Bypass REI's Minecraft singleton constructor; initialize only test-owned widget state.
		Object widget = unsafe.allocateInstance(type);
		set(widget, "font", new WidgetBehaviorCheck.CountingFont());
		Class<?> rectangle = Class.forName("me.shedaniel.math.Rectangle");
		set(widget, "bounds", rectangle.getConstructor(int.class, int.class, int.class, int.class).newInstance(20, 30, 80, 20));
		set(widget, "text", "앞뒤");
		set(widget, "cursorPos", 1);
		set(widget, "highlightPos", 1);
		set(widget, "maxLength", 32);
		set(widget, "focused", true); set(widget, "visible", true); set(widget, "editable", true);
		Class<?> cache = Class.forName("kr.playcity.hangul.PreeditCache");
		set(widget, "hangul$cache", cache.getConstructor().newInstance());
		set(widget, "filter", (java.util.function.Predicate<String>) value -> true);
		set(widget, "stripInvalid", (java.util.function.Function<String, String>) value -> value);
		AtomicInteger changed = new AtomicInteger();
		set(widget, "responder", (Consumer<String>) value -> changed.incrementAndGet());
		GuiEventListener listener = (GuiEventListener) widget;
		Method visual = type.getMethod("hangul$getVisualValue");
		for (String text : List.of("ㄱ", "가", "각", "가", "漢")) {
			check(listener.preeditUpdated(preedit(text)), "REI accepts focused preedit");
			check(visual.invoke(widget).equals("앞" + text + "뒤"), "REI immediate middle composition");
			check(get(widget, "text").equals("앞뒤") && changed.get() == 0, "REI composition does not commit/search");
		}
		listener.preeditUpdated(null);
		check(visual.invoke(widget).equals("앞뒤"), "REI candidate cancellation");
		set(widget, "cursorPos", 0); set(widget, "highlightPos", 2);
		listener.preeditUpdated(preedit("한"));
		Method wrapper = Arrays.stream(type.getDeclaredMethods()).filter(m ->
			m.getName().contains("hangul$renderInlinePreedit") && m.getParameterCount() == 5).findFirst().orElseThrow();
		wrapper.setAccessible(true);
		RuntimeException sentinel = new RuntimeException("intentional render interruption");
		try {
			wrapper.invoke(widget, null, 0, 0, 0.0f, (Operation<Void>) args -> {
				try { check(get(widget, "text").equals("한"), "REI inline selection rendering"); }
				catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
				throw sentinel;
			});
			throw new AssertionError("Expected render interruption");
		} catch (InvocationTargetException failure) { if (failure.getCause() != sentinel) throw failure; }
		check(get(widget, "text").equals("앞뒤") && get(widget, "cursorPos").equals(0)
			&& get(widget, "highlightPos").equals(2) && changed.get() == 0, "REI state restored after render exception");
		Object cachedVisual = visual.invoke(widget);
		Operation<Void> render = args -> null;
		for (int frame = 0; frame < 500; frame++) wrapper.invoke(widget, null, 0, 0, 0.0f, render);
		check(visual.invoke(widget) == cachedVisual && changed.get() == 0, "REI unchanged frames reuse visual text without search callbacks");
		set(widget, "text", "a".repeat(100)); set(widget, "cursorPos", 100); set(widget, "highlightPos", 100);
		listener.preeditUpdated(null);
		listener.preeditUpdated(preedit("한글"));
		wrapper.invoke(widget, null, 0, 0, 0.0f, (Operation<Void>) args -> {
			try { check((Integer) get(widget, "firstCharacterIndex") > 0, "REI long-query visual cursor scrolls into view"); }
			catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
			return null;
		});
		check(get(widget, "text").equals("a".repeat(100)) && get(widget, "firstCharacterIndex").equals(0), "REI scroll state restored");
		set(widget, "text", "앞뒤"); set(widget, "cursorPos", 0); set(widget, "highlightPos", 2);
		listener.preeditUpdated(null);
		listener.preeditUpdated(preedit("한"));
		type.getMethod("addText", String.class).invoke(widget, "漢");
		check(get(widget, "text").equals("漢") && visual.invoke(widget).equals("漢") && changed.get() == 1,
			"REI Hanja character commits selection exactly once");
		type.getMethod("setMaxLength", int.class).invoke(widget, 1);
		listener.preeditUpdated(preedit("글"));
		type.getMethod("addText", String.class).invoke(widget, "글");
		check(get(widget, "text").equals("漢") && visual.invoke(widget).equals("漢"), "REI length rejection clears ghost");
		listener.preeditUpdated(preedit("가"));
		type.getMethod("setVisible", boolean.class).invoke(widget, false);
		check(visual.invoke(widget).equals("漢") && !listener.preeditUpdated(preedit("나")), "hidden REI input rejects composition");
		type.getMethod("setVisible", boolean.class).invoke(widget, true);
		listener.preeditUpdated(preedit("가"));
		// Call the installed hook without REI's native focus notification.
		Method inactive = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains("hangul$onInactive")).findFirst().orElseThrow();
		inactive.setAccessible(true);
		inactive.invoke(widget, false, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("setFocused", false));
		check(visual.invoke(widget).equals("漢"), "REI focus loss releases preedit");
		System.out.println("OPTIONAL_WIDGET_OK rei=true selection, cancellation, render restoration, cached frames, scrolling, commit, limit, visibility, focus (native IME not exercised)");
	}
	private static PreeditEvent preedit(String text) { return new PreeditEvent(text, text.length(), List.of(text), 0); }
	private static Field field(Object owner, String name) throws NoSuchFieldException {
		for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
			try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
			catch (NoSuchFieldException absent) { /* Continue through widget base classes. */ }
		}
		throw new NoSuchFieldException(name);
	}
	private static Object get(Object owner, String name) throws ReflectiveOperationException { return field(owner, name).get(owner); }
	private static void set(Object owner, String name, Object value) throws ReflectiveOperationException { field(owner, name).set(owner, value); }
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
