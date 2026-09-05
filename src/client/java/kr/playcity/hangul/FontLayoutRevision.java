package kr.playcity.hangul;

/** Font metrics may change even when a widget retains the same Font instance. */
public final class FontLayoutRevision {
	private static volatile long revision;

	private FontLayoutRevision() {
	}

	public static long current() {
		return revision;
	}

	public static void invalidate() {
		revision++;
	}
}
