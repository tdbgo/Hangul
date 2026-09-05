package kr.playcity.hangul.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Exercises transformed widgets with deterministic font metrics, without a native window. */
final class WidgetBehaviorCheck {
	static void run() throws Exception {
		CountingFont font = new CountingFont();
		checkSearchTree();
		checkSingleLine(font);
		checkMultiline(font);
		System.out.println("WIDGET_BEHAVIOR_OK search, render restoration, and multiline layout reuse");
	}

	private static void checkSearchTree() throws Exception {
		SearchTree<String> tree = SearchTree.plainText(List.of("까", "가", "컈", "캐", "다이아몬드"), Stream::of);
		check(tree.search("ㄷㅇㅇㅁㄷ").equals(List.of("다이아몬드")), "transformed choseong index");
		Class<?> search = Class.forName("kr.playcity.hangul.KoreanSearch");
		Method method = search.getMethod("search", java.util.function.Function.class, String.class);
		Object result = method.invoke(null, (java.util.function.Function<String, List<String>>) tree::search, "Rk");
		check(result.equals(List.of("까")), "real search tree preserves shifted query");
	}

	private static void checkSingleLine(final CountingFont font) throws Exception {
		EditBox box = new EditBox(font, 120, 20, Component.empty());
		box.setValue("abcd");
		box.moveCursorTo(1, false);
		box.moveCursorTo(3, true);
		box.preeditUpdated(preedit("한", 1));
		Method wrapper = wrapper(EditBox.class);
		int cursor = box.getCursorPosition();
		Object selection = field(box, "highlightPos");
		RuntimeException sentinel = new RuntimeException("intentional render interruption");
		Operation<Void> interrupted = args -> {
			check(box.getValue().equals("a한d"), "single-line inline selection");
			throw sentinel;
		};
		invokeInterrupted(wrapper, box, interrupted, sentinel);
		check(box.getValue().equals("abcd") && box.getCursorPosition() == cursor
			&& field(box, "highlightPos").equals(selection), "single-line restoration after render exception");
		box.preeditUpdated(null);
	}

	private static void checkMultiline(final CountingFont font) throws Exception {
		MultiLineEditBox box = MultiLineEditBox.builder().build(font, 120, 80, Component.empty());
		String value = "첫째 줄\n둘째 줄\n".repeat(30);
		box.setValue(value);
		Object textField = field(box, "textField");
		setField(textField, "cursor", 2);
		setField(textField, "selectCursor", 5);
		box.preeditUpdated(preedit("한글", 1));
		Object originalLines = field(textField, "displayLines");
		Method wrapper = wrapper(MultiLineEditBox.class);
		Operation<Void> render = args -> {
			check(!box.getValue().equals(value), "multiline visual installed");
			return null;
		};
		invoke(wrapper, box, render);
		int measured = font.measured;
		Object cachedLines = layoutLines(box);
		for (int frame = 0; frame < 500; frame++) {
			invoke(wrapper, box, render);
		}
		check(font.measured == measured && layoutLines(box) == cachedLines, "unchanged frames do not reflow");
		check(field(textField, "displayLines") == originalLines, "original line-list identity restored");
		check(box.getValue().equals(value) && field(textField, "cursor").equals(2)
			&& field(textField, "selectCursor").equals(5), "multiline input untouched");
		box.preeditUpdated(preedit("한글", 2));
		measured = font.measured;
		invoke(wrapper, box, render);
		check(font.measured == measured && layoutLines(box) == cachedLines, "caret-only update reuses wrapping");
		Class.forName("kr.playcity.hangul.FontLayoutRevision").getMethod("invalidate").invoke(null);
		invoke(wrapper, box, render);
		check(font.measured > measured && layoutLines(box) != cachedLines, "font reload rebuilds wrapping");
		RuntimeException sentinel = new RuntimeException("intentional render interruption");
		invokeInterrupted(wrapper, box, args -> { throw sentinel; }, sentinel);
		check(box.getValue().equals(value) && field(textField, "displayLines") == originalLines,
			"multiline restoration after render exception");
		box.preeditUpdated(null);
		check(layoutLines(box) == null, "cancellation releases cached text layout");
	}

	private static Object layoutLines(final MultiLineEditBox box) throws Exception {
		return field(field(box, "hangul$layoutCache"), "layout");
	}

	private static PreeditEvent preedit(final String text, final int caret) {
		return new PreeditEvent(text, caret, List.of(text), 0);
	}

	private static Method wrapper(final Class<?> type) {
		Method method = Arrays.stream(type.getDeclaredMethods())
			.filter(m -> m.getName().contains("hangul$renderInlinePreedit") && m.getParameterCount() == 5)
			.findFirst().orElseThrow();
		method.setAccessible(true);
		return method;
	}

	private static void invoke(final Method wrapper, final Object widget, final Operation<Void> original) throws Exception {
		wrapper.invoke(widget, null, 0, 0, 0.0f, original);
	}

	private static void invokeInterrupted(final Method wrapper, final Object widget,
		final Operation<Void> original, final RuntimeException sentinel) throws Exception {
		try {
			invoke(wrapper, widget, original);
			throw new AssertionError("Expected render interruption");
		} catch (InvocationTargetException failure) {
			if (failure.getCause() != sentinel) {
				throw failure;
			}
		}
	}

	private static Object field(final Object owner, final String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static void setField(final Object owner, final String name, final Object value) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(owner, value);
	}

	private static void check(final boolean condition, final String label) {
		if (!condition) throw new AssertionError(label);
	}

	private static final class CountingFont extends Font {
		private int measured;
		private final StringSplitter testSplitter = new StringSplitter((codePoint, style) -> {
			measured++;
			return 6;
		});

		CountingFont() {
			super(null);
		}

		@Override
		public StringSplitter getSplitter() {
			return testSplitter;
		}

		@Override
		public int width(final String value) {
			return value.length() * 6;
		}

		@Override
		public int width(final FormattedText value) {
			return (int) testSplitter.stringWidth(value);
		}

		@Override
		public String plainSubstrByWidth(final String value, final int width) {
			return value.substring(0, Math.min(value.length(), Math.max(0, width / 6)));
		}
	}
}
