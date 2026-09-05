package kr.playcity.hangul;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;

import java.util.List;

/** Dependency-free deterministic checks, executed by the Gradle check task. */
public final class HangulComposerTest {
	private HangulComposerTest() {
	}

	public static void main(final String[] args) {
		InputRegressionTest.run();
		expect("안녕하세요", type("dkssudgktpdy"), "greeting");
		expect("한글", type("gksrmf"), "word");
		expect("닭", type("ekfr"), "compound final");
		expect("달가", type("ekfrk"), "compound final migration");
		expect("과", type("rhk"), "compound o vowel");
		expect("궈", type("rnj"), "compound u vowel");
		expect("긔", type("rml"), "compound eu vowel");
		expect("까따빠싸짜", type("RkEkQkTkWk"), "shifted initials");
		expect("ㅊㄴㅁ", KoreanSearch.initials("참나무"), "initial consonants");
		expectList(List.of("참나무", "ㅊㄴㅁ"), KoreanSearch.indexTerms("참나무").toList(), "search index terms");
		expect("ㅈㅇ ㅊㄴㅁ ㅍㅈ", KoreanSearch.initials("짙은 참나무 판자"), "phrase initials");
		expect("참나무", KoreanSearch.toDubeolsikHangul("ckaskan"), "Latin layout search");
		expect("참나무 판자", KoreanSearch.toDubeolsikHangul("ckaskan 판자"), "mixed layout search");
		expect("한글", KoreanSearch.toDubeolsikHangul("한글"), "Hangul layout fast path");
		expect("까", KoreanSearch.toDubeolsikHangul("Rk"), "shifted layout search");
		expect("plain", KoreanSearch.toLatinKeys("plain"), "Latin reverse-layout fast path");
		expect("gamemode", KoreanSearch.toLatinKeys(KoreanSearch.toDubeolsikHangul("gamemode")), "layout round trip");
		expect("dbg", KoreanSearch.koreanPhoneticKey("디버그"), "Korean command phonetics");
		expect("dbg", KoreanSearch.latinPhoneticKey("debug"), "Latin command phonetics");
		expect("msj", KoreanSearch.koreanPhoneticKey("메시지"), "Korean subcommand phonetics");
		expect("msj", KoreanSearch.latinPhoneticKey("message"), "Latin subcommand phonetics");
		expectList(
			List.of("참나무 판자"),
			KoreanSearch.search(
				query -> List.of("참나무 판자", "자작나무 판자").stream().filter(value -> value.contains(query)).toList(),
				"ckaskan"
			),
			"layout search result"
		);
		expectList(
			List.of("Latin hit", "shared", "Korean hit"),
			KoreanSearch.search(
				query -> switch (query) {
					case "ckaskan" -> List.of("Latin hit", "shared");
					case "참나무" -> List.of("shared", "Korean hit");
					default -> List.of();
				},
				"ckaskan"
			),
			"layout search stable union"
		);
		expectList(
			List.of("짙은 참나무 판자"),
			KoreanSearch.search(
				query -> List.of("짙은 참나무 판자", "참나무 원목").stream().filter(value -> value.contains(query)).toList(),
				"짙은\t판자"
			),
			"multi-token fallback"
		);
		expectCommandRecovery();
		expectPhoneticCommandRecovery();

		HangulComposer composer = new HangulComposer();
		StringBuilder text = new StringBuilder();
		feed(text, composer, 'r');
		feed(text, composer, 'h');
		feed(text, composer, 'k');
		expect("과", text.toString(), "backspace setup");
		apply(text, composer.backspace());
		expect("고", text.toString(), "backspace compound vowel");
		apply(text, composer.backspace());
		expect("ㄱ", text.toString(), "backspace medial");
		apply(text, composer.backspace());
		expect("", text.toString(), "backspace initial");

		composer = new HangulComposer();
		text = new StringBuilder();
		feed(text, composer, 'r');
		feed(text, composer, 'h');
		feed(text, composer, 'k');
		feed(text, composer, 'l');
		expect("\uAD18", text.toString(), "three-stage vowel setup");
		apply(text, composer.backspace());
		expect("\uACFC", text.toString(), "three-stage vowel first backspace");
		apply(text, composer.backspace());
		expect("\uACE0", text.toString(), "three-stage vowel second backspace");

		expectVisual("hello", 5, 5, "\uD55C", 1, "hello\uD55C", 6, 5, 6, "preedit at end");
		expectVisual("abXYcd", 2, 4, "\uD55C\uAE00", 1, "ab\uD55C\uAE00cd", 3, 2, 4, "preedit selection");
		expectVisual("abc", -5, 99, "\uAC00", 99, "\uAC00", 1, 0, 1, "preedit bounds");
		expectVisual(
			"first\nsecond",
			6,
			6,
			"\uD55C\uAE00",
			2,
			"first\n\uD55C\uAE00second",
			8,
			6,
			8,
			"multiline preedit"
		);
		expectVisual(
			"line one\nline two",
			5,
			13,
			"\u6F22\u5B57",
			1,
			"line \u6F22\u5B57 two",
			6,
			5,
			7,
			"multiline selection replacement"
		);

		System.out.println("HangulComposer checks passed");
	}

	private static String type(final String keys) {
		HangulComposer composer = new HangulComposer();
		StringBuilder text = new StringBuilder();
		keys.codePoints().forEach(codePoint -> feed(text, composer, codePoint));
		return text.toString();
	}

	private static void feed(final StringBuilder text, final HangulComposer composer, final int codePoint) {
		HangulComposer.Edit edit = composer.inputDubeolsik(codePoint, Character.isUpperCase(codePoint));
		if (edit == null) {
			throw new AssertionError("Unmapped test key: " + Character.toString(codePoint));
		}
		apply(text, edit);
	}

	private static void apply(final StringBuilder text, final HangulComposer.Edit edit) {
		if (edit == null) {
			return;
		}
		if (edit.replacePrevious()) {
			int end = text.length();
			int start = text.offsetByCodePoints(end, -1);
			text.delete(start, end);
		}
		text.append(edit.text());
	}

	private static void expect(final String expected, final String actual, final String caseName) {
		if (!expected.equals(actual)) {
			throw new AssertionError(caseName + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	private static void expectList(final List<String> expected, final List<String> actual, final String caseName) {
		if (!expected.equals(actual)) {
			throw new AssertionError(caseName + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	private static void expectCommandRecovery() {
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(LiteralArgumentBuilder.literal("gamemode"));
		String input = "/" + KoreanSearch.toDubeolsikHangul("game");
		StringReader reader = new StringReader(input);
		reader.skip();
		ParseResults<Object> parse = dispatcher.parse(reader, new Object());
		Suggestions vanilla = dispatcher.getCompletionSuggestions(parse, input.length()).join();
		Suggestions recovered = CommandSuggestionRecovery.augment(input, parse, input.length(), vanilla);
		if (recovered.getList().stream().noneMatch(suggestion -> suggestion.getText().equals("gamemode"))) {
			throw new AssertionError("command layout recovery: gamemode suggestion missing from " + recovered);
		}
	}

	private static void expectPhoneticCommandRecovery() {
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(
			LiteralArgumentBuilder.<Object>literal("debug")
				.then(LiteralArgumentBuilder.literal("message"))
		);
		expectRecoveredLiteral(dispatcher, "/디버그", "debug");
		expectRecoveredLiteral(dispatcher, "/debug 메시지", "message");
	}

	private static void expectRecoveredLiteral(
		final CommandDispatcher<Object> dispatcher,
		final String input,
		final String expectedLiteral
	) {
		StringReader reader = new StringReader(input);
		reader.skip();
		ParseResults<Object> parse = dispatcher.parse(reader, new Object());
		Suggestions vanilla = dispatcher.getCompletionSuggestions(parse, input.length()).join();
		Suggestions recovered = CommandSuggestionRecovery.augment(input, parse, input.length(), vanilla);
		if (recovered.getList().stream().noneMatch(suggestion -> suggestion.getText().equals(expectedLiteral))) {
			throw new AssertionError("phonetic command recovery: " + expectedLiteral + " missing from " + recovered);
		}
	}

	private static void expectVisual(
		final String value,
		final int selectionStart,
		final int selectionEnd,
		final String preedit,
		final int caret,
		final String expectedValue,
		final int expectedCursor,
		final int expectedStart,
		final int expectedEnd,
		final String caseName
	) {
		InlinePreedit.Visual visual = InlinePreedit.merge(value, selectionStart, selectionEnd, preedit, caret);
		expect(expectedValue, visual.value(), caseName + " value");
		if (visual.cursor() != expectedCursor || visual.compositionStart() != expectedStart
			|| visual.compositionEnd() != expectedEnd) {
			throw new AssertionError(caseName + ": unexpected positions " + visual);
		}
	}
}
