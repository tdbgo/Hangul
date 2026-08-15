package kr.playcity.hangul;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Allocation-conscious Korean search helpers shared by Minecraft search trees
 * and command completion.
 */
public final class KoreanSearch {
	private static final char[] INITIAL_GLYPHS = {
		'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
		'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
	};
	private static final String[] INITIAL_KEYS = {
		"r", "R", "s", "e", "E", "f", "a", "q", "Q", "t",
		"T", "d", "w", "W", "c", "z", "x", "v", "g"
	};
	private static final String[] MEDIAL_KEYS = {
		"k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho",
		"hl", "y", "n", "nj", "np", "nl", "b", "m", "ml", "l"
	};
	private static final String[] FINAL_KEYS = {
		"", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr", "fa",
		"fq", "ft", "fx", "fv", "fg", "a", "q", "qt", "t", "T",
		"d", "w", "c", "z", "x", "v", "g"
	};
	private static final String[] INITIAL_SOUNDS = {
		"g", "g", "n", "d", "d", "r", "m", "b", "b", "s",
		"s", "", "j", "j", "c", "k", "t", "p", "h"
	};
	private static final String[] FINAL_SOUNDS = {
		"", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk", "lm",
		"lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t",
		"ng", "t", "t", "k", "t", "p", "h"
	};

	private KoreanSearch() {
	}

	/** Adds the original text and, when useful, a compact initial-consonant index. */
	public static Stream<String> indexTerms(final String source) {
		if (source == null || source.isEmpty()) {
			return Stream.empty();
		}

		String normalized = normalize(source);
		String initials = initialsNormalized(normalized);
		return normalized.equals(initials)
			? Stream.of(normalized)
			: Stream.of(normalized, initials);
	}

	/** Converts 완성형 Hangul syllables to compatibility-jamo initial consonants. */
	public static String initials(final String source) {
		return initialsNormalized(normalize(source));
	}

	private static String initialsNormalized(final String normalized) {
		StringBuilder result = new StringBuilder(normalized.length());
		for (int offset = 0; offset < normalized.length();) {
			int codePoint = normalized.codePointAt(offset);
			if (codePoint >= 0xAC00 && codePoint <= 0xD7A3) {
				int initial = (codePoint - 0xAC00) / (21 * 28);
				result.append(INITIAL_GLYPHS[initial]);
			} else {
				result.appendCodePoint(codePoint);
			}
			offset += Character.charCount(codePoint);
		}
		return result.toString();
	}

	/** Converts Latin physical keys to the text produced by a standard Dubeolsik IME. */
	public static String toDubeolsikHangul(final String source) {
		if (source == null || source.isEmpty()) {
			return source == null ? "" : source;
		}
		if (!containsLatinLetter(source)) {
			return normalize(source);
		}

		HangulComposer composer = new HangulComposer();
		StringBuilder result = new StringBuilder(source.length());
		for (int offset = 0; offset < source.length();) {
			int codePoint = source.codePointAt(offset);
			HangulComposer.Edit edit = composer.inputDubeolsik(codePoint, Character.isUpperCase(codePoint));
			if (edit == null) {
				composer.commit();
				result.appendCodePoint(codePoint);
			} else {
				apply(result, edit);
			}
			offset += Character.charCount(codePoint);
		}
		composer.commit();
		return Normalizer.normalize(result, Normalizer.Form.NFC);
	}

	/**
	 * Converts composed Hangul back to the physical Dubeolsik keys that produced
	 * it. This enables zero-dictionary command recovery after typing with IME on.
	 */
	public static String toLatinKeys(final String source) {
		if (source == null || source.isEmpty()) {
			return source == null ? "" : source;
		}
		if (!containsHangul(source)) {
			return source;
		}

		String normalized = normalize(source);
		StringBuilder result = new StringBuilder(normalized.length() * 2);
		for (int offset = 0; offset < normalized.length();) {
			int codePoint = normalized.codePointAt(offset);
			appendLatinKeys(result, codePoint);
			offset += Character.charCount(codePoint);
		}
		return result.toString();
	}

	public static boolean containsHangul(final String source) {
		if (source == null) {
			return false;
		}
		for (int offset = 0; offset < source.length();) {
			int codePoint = source.codePointAt(offset);
			if ((codePoint >= 0x1100 && codePoint <= 0x11FF)
				|| (codePoint >= 0x3130 && codePoint <= 0x318F)
				|| (codePoint >= 0xAC00 && codePoint <= 0xD7A3)) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	/**
	 * Creates a compact consonant key for common Korean transliterations such as
	 * 디버그/debug. It is deliberately not a translation dictionary.
	 */
	public static String koreanPhoneticKey(final String source) {
		if (source == null || source.isEmpty()) {
			return "";
		}
		StringBuilder key = new StringBuilder(source.length());
		String normalized = normalize(source);
		for (int offset = 0; offset < normalized.length();) {
			int codePoint = normalized.codePointAt(offset);
			if (codePoint < 0xAC00 || codePoint > 0xD7A3) {
				offset += Character.charCount(codePoint);
				continue;
			}
			int syllable = codePoint - 0xAC00;
			int initial = syllable / (21 * 28);
			int medial = (syllable / 28) % 21;
			appendCollapsed(key, INITIAL_SOUNDS[initial]);
			if (initial == 11) {
				if (medial == 1 || medial == 3 || medial == 6 || medial == 7 || medial == 12 || medial == 17) {
					appendCollapsed(key, "y");
				} else if ((medial >= 9 && medial <= 11) || (medial >= 14 && medial <= 16)) {
					appendCollapsed(key, "w");
				}
			}
			appendCollapsed(key, FINAL_SOUNDS[syllable % 28]);
			offset += Character.charCount(codePoint);
		}
		return key.toString();
	}

	/** Creates the comparable consonant key for an ASCII command literal. */
	public static String latinPhoneticKey(final String source) {
		if (source == null || source.isEmpty()) {
			return "";
		}
		StringBuilder key = new StringBuilder(source.length());
		for (int index = 0; index < source.length(); index++) {
			char current = Character.toLowerCase(source.charAt(index));
			char next = index + 1 < source.length() ? Character.toLowerCase(source.charAt(index + 1)) : '\0';
			if (current < 'a' || current > 'z' || "aeiou".indexOf(current) >= 0) {
				continue;
			}
			switch (current) {
				case 'c' -> appendCollapsed(key, next == 'e' || next == 'i' || next == 'y' ? "s" : "k");
				case 'g' -> appendCollapsed(key, next == 'e' || next == 'i' || next == 'y' ? "j" : "g");
				case 'q' -> appendCollapsed(key, "k");
				case 'x' -> appendCollapsed(key, "ks");
				case 'z' -> appendCollapsed(key, "s");
				default -> appendCollapsed(key, Character.toString(current));
			}
		}
		return key.toString();
	}

	/** Searches the vanilla index first, then layout and multi-token fallbacks. */
	public static <T> List<T> search(final Function<String, List<T>> vanillaSearch, final String rawQuery) {
		String query = normalize(rawQuery == null ? "" : rawQuery).toLowerCase(Locale.ROOT);
		List<T> matches = searchTerm(vanillaSearch, query);
		if (!matches.isEmpty()) {
			return matches;
		}

		String trimmed = query.trim();
		if (!containsWhitespace(trimmed)) {
			return List.of();
		}

		ArrayList<T> intersection = null;
		for (int start = 0; start < trimmed.length();) {
			while (start < trimmed.length() && Character.isWhitespace(trimmed.charAt(start))) {
				start++;
			}
			if (start >= trimmed.length()) {
				break;
			}
			int end = start + 1;
			while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
				end++;
			}
			List<T> tokenMatches = searchTerm(vanillaSearch, trimmed.substring(start, end));
			if (tokenMatches.isEmpty()) {
				return List.of();
			}
			if (intersection == null) {
				intersection = new ArrayList<>(tokenMatches);
			} else {
				HashSet<T> allowed = new HashSet<>(tokenMatches);
				intersection.removeIf(value -> !allowed.contains(value));
			}
			if (intersection.isEmpty()) {
				return List.of();
			}
			start = end;
		}
		return intersection == null ? List.of() : intersection;
	}

	private static <T> List<T> searchTerm(
		final Function<String, List<T>> vanillaSearch,
		final String query
	) {
		List<T> primary = vanillaSearch.apply(query);
		if (!containsLatinLetter(query)) {
			return primary;
		}
		String converted = toDubeolsikHangul(query);
		if (converted.equals(query)) {
			return primary;
		}
		List<T> alternate = vanillaSearch.apply(converted.toLowerCase(Locale.ROOT));
		if (primary.isEmpty()) {
			return alternate;
		}
		if (alternate.isEmpty()) {
			return primary;
		}
		LinkedHashSet<T> merged = new LinkedHashSet<>(primary.size() + alternate.size());
		merged.addAll(primary);
		merged.addAll(alternate);
		return new ArrayList<>(merged);
	}

	private static boolean containsLatinLetter(final String value) {
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z') {
				return true;
			}
		}
		return false;
	}

	private static boolean containsWhitespace(final String value) {
		for (int index = 0; index < value.length(); index++) {
			if (Character.isWhitespace(value.charAt(index))) {
				return true;
			}
		}
		return false;
	}

	private static void apply(final StringBuilder target, final HangulComposer.Edit edit) {
		if (edit.replacePrevious() && !target.isEmpty()) {
			int end = target.length();
			int start = target.offsetByCodePoints(end, -1);
			target.delete(start, end);
		}
		target.append(edit.text());
	}

	private static void appendCollapsed(final StringBuilder target, final String value) {
		for (int index = 0; index < value.length(); index++) {
			char next = value.charAt(index);
			if (target.isEmpty() || target.charAt(target.length() - 1) != next) {
				target.append(next);
			}
		}
	}

	private static void appendLatinKeys(final StringBuilder target, final int codePoint) {
		if (codePoint >= 0xAC00 && codePoint <= 0xD7A3) {
			int syllable = codePoint - 0xAC00;
			target.append(INITIAL_KEYS[syllable / (21 * 28)]);
			target.append(MEDIAL_KEYS[(syllable / 28) % 21]);
			target.append(FINAL_KEYS[syllable % 28]);
			return;
		}
		if (codePoint >= 0x1100 && codePoint <= 0x1112) {
			target.append(INITIAL_KEYS[codePoint - 0x1100]);
			return;
		}
		if (codePoint >= 0x1161 && codePoint <= 0x1175) {
			target.append(MEDIAL_KEYS[codePoint - 0x1161]);
			return;
		}
		if (codePoint >= 0x11A8 && codePoint <= 0x11C2) {
			target.append(FINAL_KEYS[codePoint - 0x11A7]);
			return;
		}

		String compatibility = compatibilityKeys(codePoint);
		if (compatibility == null) {
			target.appendCodePoint(codePoint);
		} else {
			target.append(compatibility);
		}
	}

	private static String compatibilityKeys(final int codePoint) {
		return switch (codePoint) {
			case 'ㄱ' -> "r"; case 'ㄲ' -> "R"; case 'ㄳ' -> "rt";
			case 'ㄴ' -> "s"; case 'ㄵ' -> "sw"; case 'ㄶ' -> "sg";
			case 'ㄷ' -> "e"; case 'ㄸ' -> "E"; case 'ㄹ' -> "f";
			case 'ㄺ' -> "fr"; case 'ㄻ' -> "fa"; case 'ㄼ' -> "fq";
			case 'ㄽ' -> "ft"; case 'ㄾ' -> "fx"; case 'ㄿ' -> "fv";
			case 'ㅀ' -> "fg"; case 'ㅁ' -> "a"; case 'ㅂ' -> "q";
			case 'ㅃ' -> "Q"; case 'ㅄ' -> "qt"; case 'ㅅ' -> "t";
			case 'ㅆ' -> "T"; case 'ㅇ' -> "d"; case 'ㅈ' -> "w";
			case 'ㅉ' -> "W"; case 'ㅊ' -> "c"; case 'ㅋ' -> "z";
			case 'ㅌ' -> "x"; case 'ㅍ' -> "v"; case 'ㅎ' -> "g";
			case 'ㅏ' -> "k"; case 'ㅐ' -> "o"; case 'ㅑ' -> "i";
			case 'ㅒ' -> "O"; case 'ㅓ' -> "j"; case 'ㅔ' -> "p";
			case 'ㅕ' -> "u"; case 'ㅖ' -> "P"; case 'ㅗ' -> "h";
			case 'ㅘ' -> "hk"; case 'ㅙ' -> "ho"; case 'ㅚ' -> "hl";
			case 'ㅛ' -> "y"; case 'ㅜ' -> "n"; case 'ㅝ' -> "nj";
			case 'ㅞ' -> "np"; case 'ㅟ' -> "nl"; case 'ㅠ' -> "b";
			case 'ㅡ' -> "m"; case 'ㅢ' -> "ml"; case 'ㅣ' -> "l";
			default -> null;
		};
	}

	private static String normalize(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFC);
	}
}
