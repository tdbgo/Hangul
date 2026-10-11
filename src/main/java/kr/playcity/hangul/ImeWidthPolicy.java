package kr.playcity.hangul;

/** Conservative rules for repairing Korean IME Latin width without changing languages. */
public final class ImeWidthPolicy {
	public static final int FULL_SHAPE = 0x0008;
	public static final int NATIVE = 0x0001;
	public enum Result { REPAIRED, ALREADY_HALF_WIDTH, BUSY, NOT_KOREAN_LATIN, UNAVAILABLE }
	public record State(int language, int conversion, int sentence, boolean open, boolean busy) {}
	public interface Context extends AutoCloseable {
		State read();
		boolean write(int conversion, int sentence);
		@Override void close();
	}

	private ImeWidthPolicy() {}

	public static boolean eligible(State state) {
		return state != null && state.language() == 0x0412
			&& (state.conversion() & NATIVE) == 0 && !state.busy();
	}

	public static Result repair(Context context) {
		State before = context.read();
		if (before == null) return Result.UNAVAILABLE;
		if (before.busy()) return Result.BUSY;
		if (!eligible(before)) return Result.NOT_KOREAN_LATIN;
		if ((before.conversion() & FULL_SHAPE) == 0) return Result.ALREADY_HALF_WIDTH;
		int conversion = before.conversion() & ~FULL_SHAPE;
		if (!context.write(conversion, before.sentence())) return Result.UNAVAILABLE;
		State after = context.read();
		return after != null && after.language() == before.language() && !after.busy()
			&& after.conversion() == conversion && after.sentence() == before.sentence()
			&& after.open() == before.open() ? Result.REPAIRED : Result.UNAVAILABLE;
	}

	/** Only new fullwidth Latin letters and digits qualify; no general Unicode normalization. */
	public static int halfWidthLatin(int codepoint) {
		return codepoint >= 0xFF10 && codepoint <= 0xFF19
			|| codepoint >= 0xFF21 && codepoint <= 0xFF3A
			|| codepoint >= 0xFF41 && codepoint <= 0xFF5A ? codepoint - 0xFEE0 : codepoint;
	}
}
