package kr.playcity.hangul;

import java.util.List;

/** Regression coverage for search, widget ownership, and composition cache boundaries. */
final class InputRegressionTest {
	static void run() {
		for (String[] example : new String[][] {{"Rk", "까"}, {"zO", "컈"}, {"Tho", "쐐"}, {"rP", "계"}}) {
			List<String> actual = KoreanSearch.search(
				query -> List.of("가", "까", "캐", "컈", "쐐", "계").stream()
					.filter(value -> value.contains(query)).toList(), example[0]);
			equal(List.of(example[1]), actual, "shifted search " + example[0]);
		}
		equal(List.of("까 철"), KoreanSearch.search(
			query -> List.of("까 철", "가 철").stream().filter(value -> value.contains(query)).toList(),
			"철\tRk"), "shifted multi-token search");
		equal(List.of("Latin", "shared", "Korean"), KoreanSearch.search(query -> switch (query) {
			case "rk" -> List.of("Latin", "shared");
			case "까" -> List.of("shared", "Korean");
			default -> List.of();
		}, "Rk"), "case-insensitive primary and case-sensitive layout union");
		equal(List.of(), KoreanSearch.search(query -> List.of(), null), "null query");

		HangulComposer composer = new HangulComposer();
		for (int codePoint : new int[] {-1, 0, ' ', '0', 0x10061, 0x10052, 0x1F600, 0x212A, 0x110000}) {
			check(composer.inputDubeolsik(codePoint, false) == null, "non-ASCII key " + codePoint);
			check(!composer.isComposing(), "rejected key changed composer");
		}
		String outsideBmp = Character.toString(0x10061);
		equal(outsideBmp + "ㅁ", KoreanSearch.toDubeolsikHangul(outsideBmp + "a"), "supplementary text preservation");
		equal("가😀까", KoreanSearch.toDubeolsikHangul("rk😀Rk"), "emoji composition boundary");
		for (int syllable = 0xAC00; syllable <= 0xD7A3; syllable++) {
			String text = Character.toString(syllable);
			equal(text, KoreanSearch.toDubeolsikHangul(KoreanSearch.toLatinKeys(text)), "syllable " + syllable);
		}
		checkSession();
		checkCaches();
		System.out.println("Input regressions passed, including all 11,172 modern Hangul syllables");
	}

	private static void checkSession() {
		FallbackSession session = new FallbackSession();
		Object widget = new Object(), other = new Object(), screen = new Object(), nextScreen = new Object();
		check(!session.toggle(null, screen) && !session.active(), "no input target");
		check(session.toggle(widget, screen), "enable fallback");
		check(session.owns(widget, screen) && !session.owns(other) && !session.owns(widget, nextScreen), "owner isolation");
		session.composer().inputDubeolsik('r', false);
		session.acceptEdit("ㄱ", 1, "ㄱ", 1, false);
		session.validateAnchor("ㄱ", 1, false);
		check(session.composer().isComposing(), "accepted edit anchor");
		session.release(other);
		check(session.active(), "unrelated widget release");
		session.release(widget);
		check(!session.active() && !session.composer().isComposing(), "focus loss resets composition and mode");
		session.toggle(widget, screen);
		session.composer().inputDubeolsik('r', false);
		session.acceptEdit("abcㄱ", 4, "abc", 3, false);
		check(!session.composer().isComposing(), "length limit or filter rejection");
		session.composer().inputDubeolsik('r', false);
		session.acceptEdit("ㄱ", 1, "ㄱ", 1, false);
		session.validateAnchor("ㄱ", 0, false);
		check(!session.composer().isComposing(), "cursor movement commits");
		session.composer().inputDubeolsik('r', false);
		session.acceptEdit("ㄱ", 1, "ㄱ", 1, false);
		session.validateAnchor("ㄱ", 1, true);
		check(!session.composer().isComposing(), "selection commits");
		session.clear();
		check(!session.active(), "screen removal resets mode");
		check(session.toggle(widget, screen) && !session.toggle(widget, screen), "second F6 restores native mode");
	}

	private static void checkCaches() {
		PreeditCache cache = new PreeditCache();
		InlinePreedit.Visual first = cache.get("abcd", 1, 2, "한", 1);
		for (int frame = 0; frame < 100_000; frame++) {
			check(first == cache.get("abcd", 1, 2, "한", 1), "stable frame reuses visual");
		}
		equal("a한cd", first.value(), "cached visual");
		check(first != cache.get("abcd", 1, 2, "한", 0), "caret invalidation");
		equal("a한d", cache.get("abcd", 1, 3, "한", 1).value(), "selection invalidation");
		equal("a漢d", cache.get("abcd", 1, 3, "漢", 1).value(), "conversion invalidation");
		equal("a漢de", cache.get("abcde", 1, 3, "漢", 1).value(), "body invalidation");
		cache.clear();
		check(first != cache.get("abcd", 1, 2, "한", 1), "cancel clears visual");

		LayoutCache<Object> layouts = new LayoutCache<>();
		Object lines = new Object();
		layouts.put(first.value(), 100, 1, lines);
		for (int frame = 0; frame < 100_000; frame++) {
			check(lines == layouts.get(first.value(), 100, 1), "stable layout reuse");
		}
		check(layouts.get(first.value(), 101, 1) == null, "width invalidation");
		check(layouts.get(first.value(), 100, 2) == null, "font reload invalidation");
		check(layouts.get("different", 100, 1) == null, "layout text invalidation");
		layouts.clear();
		check(layouts.get(first.value(), 100, 1) == null, "cancel clears layout");
	}

	private static void equal(final Object expected, final Object actual, final String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(final boolean condition, final String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
