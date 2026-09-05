package kr.playcity.hangul;

/** Bounded layout cache, independent of cursor movement and invalidated by font reloads. */
public final class LayoutCache<T> {
	private String value;
	private int width;
	private long fontRevision;
	private T layout;

	public T get(final String value, final int width, final long fontRevision) {
		return value.equals(this.value) && width == this.width && fontRevision == this.fontRevision
			? layout : null;
	}

	public void put(final String value, final int width, final long fontRevision, final T layout) {
		this.value = value;
		this.width = width;
		this.fontRevision = fontRevision;
		this.layout = layout;
	}

	public void clear() {
		value = null;
		layout = null;
	}
}
