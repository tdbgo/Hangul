package kr.playcity.hangul;

/** Pure text transformation used only while rendering an OS IME composition. */
public final class InlinePreedit {
	private InlinePreedit() {
	}

	public static Visual merge(
		final String value,
		final int selectionStart,
		final int selectionEnd,
		final String preedit,
		final int caretPosition
	) {
		int start = clamp(Math.min(selectionStart, selectionEnd), 0, value.length());
		int end = clamp(Math.max(selectionStart, selectionEnd), start, value.length());
		int caret = clamp(caretPosition, 0, preedit.length());
		String visualValue = value.substring(0, start) + preedit + value.substring(end);
		return new Visual(visualValue, start + caret, start, start + preedit.length());
	}

	private static int clamp(final int value, final int minimum, final int maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}

	public record Visual(String value, int cursor, int compositionStart, int compositionEnd) {
	}
}
