package kr.playcity.hangul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Bridges the UI ownership move from {@link Minecraft} to {@code Gui} in 26.2. */
final class MinecraftUiCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("hangul");

	private static final Method GUI_SCREEN = method(
		"net.minecraft.client.gui.Gui", "screen"
	);
	private static final Method GUI_OVERLAY = method(
		"net.minecraft.client.gui.Gui", "overlay"
	);
	private static final Method GUI_SET_OVERLAY_MESSAGE = method(
		"net.minecraft.client.gui.Gui", "setOverlayMessage", Component.class, boolean.class
	);
	private static final Field GUI_HUD = field("net.minecraft.client.gui.Gui", "hud");
	private static final Method HUD_SET_OVERLAY_MESSAGE = GUI_HUD == null ? null : method(
		GUI_HUD.getType(), "setOverlayMessage", Component.class, boolean.class
	);

	private static final Field MINECRAFT_SCREEN = field(Minecraft.class, "screen");
	private static final Method MINECRAFT_GET_OVERLAY = method(Minecraft.class, "getOverlay");

	private MinecraftUiCompat() {
	}

	static Screen screen(final Minecraft minecraft) {
		if (GUI_SCREEN != null) {
			return (Screen) invoke(GUI_SCREEN, minecraft.gui);
		}
		if (MINECRAFT_SCREEN != null) {
			return (Screen) get(MINECRAFT_SCREEN, minecraft);
		}
		return null;
	}

	static boolean hasOverlay(final Minecraft minecraft) {
		if (GUI_OVERLAY != null) {
			return invoke(GUI_OVERLAY, minecraft.gui) != null;
		}
		return MINECRAFT_GET_OVERLAY != null && invoke(MINECRAFT_GET_OVERLAY, minecraft) != null;
	}

	static void showStatus(final Minecraft minecraft, final Component message) {
		if (GUI_HUD != null && HUD_SET_OVERLAY_MESSAGE != null) {
			Object hud = get(GUI_HUD, minecraft.gui);
			if (hud != null) {
				invoke(HUD_SET_OVERLAY_MESSAGE, hud, message, false);
				return;
			}
		}
		if (GUI_SET_OVERLAY_MESSAGE != null) {
			invoke(GUI_SET_OVERLAY_MESSAGE, minecraft.gui, message, false);
		}
	}

	private static Method method(final String className, final String name, final Class<?>... parameterTypes) {
		try {
			return method(Class.forName(className), name, parameterTypes);
		} catch (ClassNotFoundException | LinkageError exception) {
			return null;
		}
	}

	private static Method method(final Class<?> owner, final String name, final Class<?>... parameterTypes) {
		try {
			return owner.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException | LinkageError exception) {
			return null;
		}
	}

	private static Field field(final String className, final String name) {
		try {
			return field(Class.forName(className), name);
		} catch (ClassNotFoundException | LinkageError exception) {
			return null;
		}
	}

	private static Field field(final Class<?> owner, final String name) {
		try {
			return owner.getField(name);
		} catch (NoSuchFieldException | LinkageError exception) {
			return null;
		}
	}

	private static Object invoke(final Method method, final Object target, final Object... arguments) {
		try {
			return method.invoke(target, arguments);
		} catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
			LOGGER.warn("Minecraft UI compatibility call {} failed", method.getName(), exception);
			return null;
		}
	}

	private static Object get(final Field field, final Object target) {
		try {
			return field.get(target);
		} catch (IllegalAccessException | LinkageError exception) {
			LOGGER.warn("Minecraft UI compatibility field {} failed", field.getName(), exception);
			return null;
		}
	}
}
