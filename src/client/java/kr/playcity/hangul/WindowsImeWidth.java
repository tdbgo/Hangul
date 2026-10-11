package kr.playcity.hangul;

import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Uses Minecraft's existing JNA library; no native binary is shipped by Hangul. */
final class WindowsImeWidth {
	private final Function foreground, windowThread, currentThread, currentProcess, layout;
	private final Function getContext, releaseContext, getConversion, setConversion, composition, candidates, open;
	private final Method windowProperties, pointerProperty, glfwWindow;

	WindowsImeWidth() throws ReflectiveOperationException {
		NativeLibrary user = NativeLibrary.getInstance("user32");
		NativeLibrary kernel = NativeLibrary.getInstance("kernel32");
		NativeLibrary imm = NativeLibrary.getInstance("imm32");
		foreground = function(user, "GetForegroundWindow");
		windowThread = function(user, "GetWindowThreadProcessId");
		layout = function(user, "GetKeyboardLayout");
		currentThread = function(kernel, "GetCurrentThreadId");
		currentProcess = function(kernel, "GetCurrentProcessId");
		getContext = function(imm, "ImmGetContext");
		releaseContext = function(imm, "ImmReleaseContext");
		getConversion = function(imm, "ImmGetConversionStatus");
		setConversion = function(imm, "ImmSetConversionStatus");
		composition = function(imm, "ImmGetCompositionStringW");
		candidates = function(imm, "ImmGetCandidateListCountW");
		open = function(imm, "ImmGetOpenStatus");
		// Resolve from the game's actual backend, not from the presence of an unrelated mod's library.
		boolean glfw = Arrays.stream(Window.class.getDeclaredMethods()).anyMatch(method -> method.getName().equals("createGlfwWindow"));
		if (glfw) {
			glfwWindow = Class.forName("org.lwjgl.glfw.GLFWNativeWin32").getMethod("glfwGetWin32Window", long.class);
			windowProperties = pointerProperty = null;
		} else {
			windowProperties = Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowProperties", long.class);
			pointerProperty = Class.forName("org.lwjgl.sdl.SDLProperties")
				.getMethod("SDL_GetPointerProperty", int.class, CharSequence.class, long.class);
			glfwWindow = null;
		}
	}

	private static Function function(NativeLibrary library, String name) {
		return library.getFunction(name, Function.ALT_CONVENTION);
	}

	ImeWidthPolicy.Context acquire(long gameHandle) throws ReflectiveOperationException {
		long hwnd = glfwWindow != null ? (long) glfwWindow.invoke(null, gameHandle)
			: (long) pointerProperty.invoke(null, (int) windowProperties.invoke(null, gameHandle), "SDL.window.win32.hwnd", 0L);
		if (hwnd == 0) return null;
		Pointer window = Pointer.createConstant(hwnd);
		if (!ownsForeground(window)) return null;
		Pointer context = getContext.invokePointer(new Object[]{window});
		return context == null ? null : new Context(window, context);
	}

	private boolean ownsForeground(Pointer window) {
		if (!window.equals(foreground.invokePointer(new Object[0]))) return false;
		try (Memory process = new Memory(4)) {
			int thread = windowThread.invokeInt(new Object[]{window, process});
			return thread != 0 && thread == currentThread.invokeInt(new Object[0])
				&& process.getInt(0) == currentProcess.invokeInt(new Object[0]);
		}
	}

	private final class Context implements ImeWidthPolicy.Context {
		private final Pointer window, context;
		private boolean closed;
		Context(Pointer window, Pointer context) { this.window = window; this.context = context; }
		@Override public ImeWidthPolicy.State read() {
			if (closed || !ownsForeground(window)) return null;
			Pointer keyboard = layout.invokePointer(new Object[]{0});
			int language = (int) (Pointer.nativeValue(keyboard) & 0xFFFF);
			try (Memory modes = new Memory(12)) {
				modes.clear();
				if (getConversion.invokeInt(new Object[]{context, modes, modes.share(4)}) == 0) return null;
				// Query lengths only. Neither input text nor candidate strings are read or logged.
				int composing = composition.invokeInt(new Object[]{context, 8, null, 0});
				if (composing < 0) return null;
				int candidateBytes = candidates.invokeInt(new Object[]{context, modes.share(8)});
				return new ImeWidthPolicy.State(language, modes.getInt(0), modes.getInt(4),
					open.invokeInt(new Object[]{context}) != 0,
					composing > 0 || candidateBytes != 0 || modes.getInt(8) != 0);
			}
		}
		@Override public boolean write(int conversion, int sentence) {
			if (closed || !ownsForeground(window)) return false;
			// Recheck immediately before changing a native mode; focus or language may have changed.
			ImeWidthPolicy.State state = read();
			return ImeWidthPolicy.eligible(state) && conversion == (state.conversion() & ~ImeWidthPolicy.FULL_SHAPE)
				&& sentence == state.sentence() && setConversion.invokeInt(new Object[]{context, conversion, sentence}) != 0;
		}
		@Override public void close() {
			if (!closed) { closed = true; releaseContext.invokeInt(new Object[]{window, context}); }
		}
	}
}
