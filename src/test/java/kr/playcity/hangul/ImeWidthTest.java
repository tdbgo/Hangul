package kr.playcity.hangul;

import java.nio.file.Files;
import java.util.List;

final class ImeWidthTest {
	static void run() {
		for (int codepoint = 0; codepoint <= Character.MAX_CODE_POINT; codepoint++) {
			int actual = ImeWidthPolicy.halfWidthLatin(codepoint);
			boolean target = codepoint >= 0xFF10 && codepoint <= 0xFF19
				|| codepoint >= 0xFF21 && codepoint <= 0xFF3A || codepoint >= 0xFF41 && codepoint <= 0xFF5A;
			check(actual == (target ? codepoint - 0xFEE0 : codepoint), "width conversion range");
		}
		check(ImeWidthPolicy.halfWidthLatin(-1) == -1, "invalid codepoint preservation");
		check(ImeWidthPolicy.halfWidthLatin(0x110000) == 0x110000, "out-of-range preservation");
		int flags = 0x0200 | 0x0020 | ImeWidthPolicy.FULL_SHAPE;
		FakeContext context = new FakeContext(new ImeWidthPolicy.State(0x0412, flags, 9, false, false));
		check(ImeWidthPolicy.repair(context) == ImeWidthPolicy.Result.REPAIRED, "Korean Latin repair");
		check(context.state.conversion() == (flags & ~8) && context.state.sentence() == 9
			&& !context.state.open() && context.writes == 1, "preserve all other flags and open state");
		check(ImeWidthPolicy.repair(context) == ImeWidthPolicy.Result.ALREADY_HALF_WIDTH && context.writes == 1,
			"already half-width has no native write");
		for (ImeWidthPolicy.State state : List.of(
				new ImeWidthPolicy.State(0x0412, flags | ImeWidthPolicy.NATIVE, 0, true, false),
				new ImeWidthPolicy.State(0x0411, flags, 0, true, false),
				new ImeWidthPolicy.State(0x0804, flags, 0, true, false),
				new ImeWidthPolicy.State(0x0409, flags, 0, true, false),
				new ImeWidthPolicy.State(0x0412, flags, 0, true, true))) {
			context = new FakeContext(state);
			check(!ImeWidthPolicy.eligible(state), "protected input mode");
			check(ImeWidthPolicy.repair(context) != ImeWidthPolicy.Result.REPAIRED && context.writes == 0,
				"protected mode has no native write");
		}
		context = new FakeContext(null);
		check(!ImeWidthPolicy.eligible(null) && ImeWidthPolicy.repair(context) == ImeWidthPolicy.Result.UNAVAILABLE,
			"unavailable state fails closed");
		context = new FakeContext(new ImeWidthPolicy.State(0x0412, flags, 3, true, false));
		context.accept = false;
		check(ImeWidthPolicy.repair(context) == ImeWidthPolicy.Result.UNAVAILABLE && context.state.conversion() == flags,
			"rejected native write is not success");
		for (int fault = 1; fault <= 5; fault++) {
			context = new FakeContext(new ImeWidthPolicy.State(0x0412, flags, 3, true, false));
			context.fault = fault;
			check(ImeWidthPolicy.repair(context) == ImeWidthPolicy.Result.UNAVAILABLE, "readback mismatch " + fault);
		}
		checkPreferences();
		System.out.println("IME_WIDTH_POLICY_OK all Unicode codepoints, protected modes, native failures, readback, and preferences");
	}

	private static void checkPreferences() {
		try {
			var directory = Files.createTempDirectory("hangul-ime-test-");
			var path = directory.resolve("hangul.properties");
			try {
				check(!ImePreferences.load(path) && !Files.exists(path), "default off without creating configuration");
				Files.writeString(path, "halfWidthLatin=invalid\notherSetting=preserved\n");
				check(!ImePreferences.load(path), "invalid value defaults off");
				ImePreferences.save(path, true);
				check(ImePreferences.load(path) && Files.readString(path).contains("otherSetting=preserved"), "persist and preserve other settings");
				ImePreferences.save(path, false);
				check(!ImePreferences.load(path), "persist disabled");
				try (var files = Files.list(directory)) { check(files.count() == 1, "no temporary configuration remains"); }
			} finally { Files.deleteIfExists(path); Files.delete(directory); }
		} catch (Exception exception) { throw new AssertionError("IME preference regression", exception); }
	}

	private static final class FakeContext implements ImeWidthPolicy.Context {
		ImeWidthPolicy.State state;
		int writes, fault;
		boolean accept = true;
		FakeContext(ImeWidthPolicy.State state) { this.state = state; }
		@Override public ImeWidthPolicy.State read() { return state; }
		@Override public boolean write(int conversion, int sentence) {
			writes++;
			if (!accept) return false;
			state = switch (fault) {
				case 1 -> null;
				case 2 -> new ImeWidthPolicy.State(0x0411, conversion, sentence, state.open(), false);
				case 3 -> new ImeWidthPolicy.State(state.language(), conversion | 8, sentence, state.open(), false);
				case 4 -> new ImeWidthPolicy.State(state.language(), conversion, sentence + 1, state.open(), false);
				case 5 -> new ImeWidthPolicy.State(state.language(), conversion, sentence, !state.open(), false);
				default -> new ImeWidthPolicy.State(state.language(), conversion, sentence, state.open(), false);
			};
			return true;
		}
		@Override public void close() {}
	}

	private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
