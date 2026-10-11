package kr.playcity.hangul.verification;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;

/** Checks shipped event code and native function binding, never changes an OS input mode. */
final class ImeWidthBehaviorCheck {
	static void run() throws Exception {
		Class<?> controller = Class.forName("kr.playcity.hangul.ImeWidthController");
		Method dispatch = controller.getMethod("onCharacter", long.class, CharacterEvent.class);
		CharacterEvent full = new CharacterEvent(0xFF21);
		check(dispatch.invoke(null, 0L, full) == full, "default guard is off and invalid window passes through");
		check(!(boolean) field(controller, "enabled").get(null), "default setting off");
		Method keyboardHook = Arrays.stream(KeyboardHandler.class.getDeclaredMethods())
			.filter(method -> method.getName().contains("hangul$guardLatinWidth")).findFirst().orElseThrow();
		check(keyboardHook.getReturnType() == CharacterEvent.class, "central character hook installed");
		Class<?> contextType = Class.forName("kr.playcity.hangul.ImeWidthPolicy$Context");
		Class<?> stateType = Class.forName("kr.playcity.hangul.ImeWidthPolicy$State");
		Constructor<?> state = stateType.getConstructor(int.class, int.class, int.class, boolean.class, boolean.class);
		Method guard = controller.getDeclaredMethod("guardCharacter", contextType, CharacterEvent.class);
		guard.setAccessible(true);
		for (int variant = 0; variant < 8; variant++) {
			int example = variant;
			Object[] current = {state.newInstance(variant == 2 ? 0x0411 : 0x0412, variant == 3 ? 9 : 8, 3, false, variant == 4)};
			int[] writes = {0};
			Object context = Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType}, (proxy, method, args) -> {
				return switch (method.getName()) {
					case "read" -> current[0];
					case "write" -> {
						writes[0]++;
						if (example == 1) yield false;
						current[0] = example == 5 ? null : state.newInstance(0x0412, args[0],
							example == 6 ? 4 : args[1], example == 7, false);
						yield true;
					}
					case "close" -> null;
					default -> throw new AssertionError(method.getName());
				};
			});
			CharacterEvent result = (CharacterEvent) guard.invoke(null, context, full);
			check(variant < 2 ? result.codepoint() == 'A' : result == full, "character protection variant " + variant);
			check(writes[0] == (variant >= 2 && variant <= 4 ? 0 : 1), "write gating variant " + variant);
			CharacterEvent punctuation = new CharacterEvent(0xFF01);
			check(guard.invoke(null, context, punctuation) == punctuation, "fullwidth punctuation untouched");
		}
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
			Class<?> nativeType = Class.forName("kr.playcity.hangul.WindowsImeWidth");
			Constructor<?> constructor = nativeType.getDeclaredConstructor();
			constructor.setAccessible(true);
			Object backend = constructor.newInstance();
			Class<?> function = Class.forName("com.sun.jna.Function");
			Method invoke = function.getMethod("invokeInt", Object[].class);
			int process = (int) invoke.invoke(field(nativeType, "currentProcess").get(backend), (Object) new Object[0]);
			int thread = (int) invoke.invoke(field(nativeType, "currentThread").get(backend), (Object) new Object[0]);
			check(Integer.toUnsignedLong(process) == ProcessHandle.current().pid() && thread != 0, "Win32 function ABI");
			System.out.println("WINDOWS_IME_BINDINGS_OK (read-only ABI check; no window, IME mode or text changed)");
		}
		System.out.println("IME_WIDTH_EVENTS_OK default passthrough, protected modes, failed writes, and readback validation");
	}

	private static Field field(Class<?> owner, String name) throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
	private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
