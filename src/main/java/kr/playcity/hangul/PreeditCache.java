package kr.playcity.hangul;

/** Single-entry cache; unchanged composition frames reuse the same visual text. */
public final class PreeditCache {
	private String value;
	private String preedit;
	private int start;
	private int end;
	private int caret;
	private InlinePreedit.Visual visual;

	public InlinePreedit.Visual get(final String value, final int start, final int end,
		final String preedit, final int caret) {
		if (visual == null || !value.equals(this.value) || !preedit.equals(this.preedit)
			|| start != this.start || end != this.end || caret != this.caret) {
			visual = InlinePreedit.merge(value, start, end, preedit, caret);
			this.value = value;
			this.preedit = preedit;
			this.start = start;
			this.end = end;
			this.caret = caret;
		}
		return visual;
	}

	public void clear() {
		value = null;
		preedit = null;
		visual = null;
	}
}
