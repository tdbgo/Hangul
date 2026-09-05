package kr.playcity.hangul;

/** Emergency composition belongs to one focused widget in one screen. */
public final class FallbackSession {
	private final HangulComposer composer = new HangulComposer();
	private Object owner;
	private Object screen;
	private String anchorValue;
	private int anchorCursor;

	public HangulComposer composer() {
		return composer;
	}

	public boolean active() {
		return owner != null;
	}

	public boolean owns(final Object widget) {
		return owner != null && owner == widget;
	}

	public boolean owns(final Object widget, final Object currentScreen) {
		return owns(widget) && screen == currentScreen;
	}

	public boolean toggle(final Object widget, final Object currentScreen) {
		boolean enable = widget != null && currentScreen != null && !owns(widget, currentScreen);
		clear();
		if (enable) {
			owner = widget;
			screen = currentScreen;
		}
		return enable;
	}

	public void release(final Object widget) {
		if (owns(widget)) {
			clear();
		}
	}

	public void clear() {
		commit();
		owner = null;
		screen = null;
	}

	public void commit() {
		composer.commit();
		anchorValue = null;
		anchorCursor = 0;
	}

	public void validateAnchor(final String value, final int cursor, final boolean hasSelection) {
		if (composer.isComposing()
			&& (hasSelection || cursor != anchorCursor || !value.equals(anchorValue))) {
			commit();
		}
	}

	/** A length limit or field filter must not leave a composer pointing at unrelated text. */
	public void acceptEdit(final String expectedValue, final int expectedCursor,
		final String actualValue, final int actualCursor, final boolean hasSelection) {
		if (!expectedValue.equals(actualValue) || expectedCursor != actualCursor || hasSelection) {
			commit();
		} else if (composer.isComposing()) {
			anchorValue = actualValue;
			anchorCursor = actualCursor;
		} else {
			commit();
		}
	}
}
