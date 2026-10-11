package kr.playcity.hangul;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Event-driven, opt-in width guard. No tick polling, synthetic keys or input-buffer rewrites. */
public final class ImeWidthController {
	private static final Logger LOGGER = LoggerFactory.getLogger("hangul");
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	private static long inputWindow;
	private static boolean enabled, loaded, unavailable;
	private static Path preferences;
	private static WindowsImeWidth backend;

	private ImeWidthController() {}

	public static void onTextInputChanged(long handle, boolean active) {
		inputWindow = active ? handle : 0;
		if (!WINDOWS || !active || handle == 0) return;
		loadPreferences();
		if (enabled) repair(handle);
	}

	public static boolean onKeyPress(long handle, int action, KeyEvent event) {
		if (!WINDOWS || inputWindow == 0 || handle != inputWindow || event.key() != InputKeyCompat.KEY_F7
			|| event.hasControlDownWithQuirk() || event.hasAltDown() || (event.modifiers() & InputKeyCompat.MOD_SUPER) != 0) return false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !minecraft.isWindowActive() || handle != minecraft.getWindow().handle()) return false;
		if (action != InputKeyCompat.ACTION_PRESS) return true;
		loadPreferences();
		if (event.hasShiftDown()) {
			enabled = !enabled;
			if (preferences != null) {
				try { ImePreferences.save(preferences, enabled); }
				catch (IOException | IllegalArgumentException exception) { LOGGER.warn("Could not save Hangul IME setting; it applies to this session only"); }
			}
			status(minecraft, enabled ? "반각 영문 유지: 켜짐" : "반각 영문 유지: 꺼짐");
			if (enabled) repair(handle);
		} else {
			String message = switch (repair(handle)) {
				case REPAIRED -> "반각 영문 복구 완료";
				case ALREADY_HALF_WIDTH -> "이미 반각 영문 상태입니다";
				case BUSY -> "조합·한자 후보 선택을 마친 뒤 다시 시도하세요";
				case NOT_KOREAN_LATIN -> "한국어 IME의 영문 상태에서 사용할 수 있습니다";
				case UNAVAILABLE -> "이 입력기에서는 반각 상태를 확인하거나 복구할 수 없습니다";
			};
			status(minecraft, message);
		}
		return true;
	}

	public static CharacterEvent onCharacter(long handle, CharacterEvent event) {
		int normalized = ImeWidthPolicy.halfWidthLatin(event.codepoint());
		if (!enabled || inputWindow == 0 || handle != inputWindow || normalized == event.codepoint()) return event;
		try (ImeWidthPolicy.Context context = acquire(handle)) {
			return context == null ? event : guardCharacter(context, event);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			disable(exception);
			return event;
		}
	}

	private static CharacterEvent guardCharacter(ImeWidthPolicy.Context context, CharacterEvent event) {
		int normalized = ImeWidthPolicy.halfWidthLatin(event.codepoint());
		if (normalized == event.codepoint()) return event;
		ImeWidthPolicy.State before = context.read();
		if (!ImeWidthPolicy.eligible(before)) return event;
		// Correct the committed character even if an IME refuses the optional width-only write.
		ImeWidthPolicy.repair(context);
		ImeWidthPolicy.State after = context.read();
		boolean preserved = ImeWidthPolicy.eligible(after) && before.language() == after.language()
			&& (before.conversion() & ~ImeWidthPolicy.FULL_SHAPE) == (after.conversion() & ~ImeWidthPolicy.FULL_SHAPE)
			&& before.sentence() == after.sentence() && before.open() == after.open();
		return preserved ? new CharacterEvent(normalized) : event;
	}

	private static ImeWidthPolicy.Result repair(long handle) {
		try (ImeWidthPolicy.Context context = acquire(handle)) {
			return context == null ? ImeWidthPolicy.Result.UNAVAILABLE : ImeWidthPolicy.repair(context);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			disable(exception);
			return ImeWidthPolicy.Result.UNAVAILABLE;
		}
	}

	private static ImeWidthPolicy.Context acquire(long handle) throws ReflectiveOperationException {
		if (!WINDOWS || unavailable || handle == 0) return null;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !minecraft.isWindowActive() || minecraft.getWindow().handle() != handle) return null;
		if (backend == null) backend = new WindowsImeWidth();
		return backend.acquire(handle);
	}

	private static void loadPreferences() {
		if (loaded) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return;
		loaded = true;
		preferences = minecraft.gameDirectory.toPath().resolve("config/hangul.properties");
		try { enabled = ImePreferences.load(preferences); }
		catch (IOException | IllegalArgumentException exception) { LOGGER.warn("Could not read Hangul IME setting; width guard remains off"); }
	}

	private static void status(Minecraft minecraft, String message) {
		MinecraftUiCompat.showStatus(minecraft, Component.literal(message));
	}

	private static void disable(Throwable exception) {
		if (!unavailable) LOGGER.warn("Windows IME width support unavailable ({}); native text input is unchanged", exception.getClass().getSimpleName());
		unavailable = true;
	}
}
