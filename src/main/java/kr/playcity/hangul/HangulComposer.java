package kr.playcity.hangul;

/**
 * A small, allocation-conscious Dubeolsik (two-set Korean) composer.
 *
 * <p>The composer owns only the currently editable code point. Callers keep
 * already committed text and apply each {@link Edit} to their text field.
 * This keeps the algorithm independent of Minecraft and native IME APIs.</p>
 */
public final class HangulComposer {
	private static final int NO_JAMO = -1;

	private static final char[] INITIAL_GLYPHS = {
		'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
		'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
	};

	private static final char[] MEDIAL_GLYPHS = {
		'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
		'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
	};

	private static final int[] INITIAL_TO_FINAL = {
		1, 2, 4, 7, 0, 8, 16, 17, 0, 19, 20, 21, 22, 0, 23, 24, 25, 26, 27
	};

	private static final int[] FINAL_TO_INITIAL = {
		-1, 0, 1, -1, 2, -1, -1, 3, 5, -1, -1, -1, -1, -1,
		-1, -1, 6, 7, -1, 9, 10, 11, 12, 14, 15, 16, 17, 18
	};

	private int initial = NO_JAMO;
	private int medial = NO_JAMO;
	private int finalConsonant;
	private final int[] medialHistory = new int[2];
	private int medialHistorySize;

	/**
	 * Feeds one Latin character using the standard Korean two-set layout.
	 *
	 * @return an edit, or {@code null} when the character is not a layout key
	 */
	public Edit inputDubeolsik(final int codePoint, final boolean shifted) {
		char key = Character.toLowerCase((char)codePoint);
		return switch (key) {
			case 'r' -> this.inputConsonant(shifted ? 1 : 0);
			case 's' -> this.inputConsonant(2);
			case 'e' -> this.inputConsonant(shifted ? 4 : 3);
			case 'f' -> this.inputConsonant(5);
			case 'a' -> this.inputConsonant(6);
			case 'q' -> this.inputConsonant(shifted ? 8 : 7);
			case 't' -> this.inputConsonant(shifted ? 10 : 9);
			case 'd' -> this.inputConsonant(11);
			case 'w' -> this.inputConsonant(shifted ? 13 : 12);
			case 'c' -> this.inputConsonant(14);
			case 'z' -> this.inputConsonant(15);
			case 'x' -> this.inputConsonant(16);
			case 'v' -> this.inputConsonant(17);
			case 'g' -> this.inputConsonant(18);
			case 'k' -> this.inputVowel(0);
			case 'o' -> this.inputVowel(shifted ? 3 : 1);
			case 'i' -> this.inputVowel(2);
			case 'j' -> this.inputVowel(4);
			case 'p' -> this.inputVowel(shifted ? 7 : 5);
			case 'u' -> this.inputVowel(6);
			case 'h' -> this.inputVowel(8);
			case 'y' -> this.inputVowel(12);
			case 'n' -> this.inputVowel(13);
			case 'b' -> this.inputVowel(17);
			case 'm' -> this.inputVowel(18);
			case 'l' -> this.inputVowel(20);
			default -> null;
		};
	}

	/** Removes one composition stage rather than deleting the whole syllable. */
	public Edit backspace() {
		String before = this.render();
		if (before.isEmpty()) {
			return null;
		}

		if (this.finalConsonant != 0) {
			int split = splitFinal(this.finalConsonant);
			this.finalConsonant = split == 0 ? 0 : split >>> 8;
		} else if (this.medial != NO_JAMO) {
			if (this.medialHistorySize > 0) {
				this.medial = this.medialHistory[--this.medialHistorySize];
			} else if (this.initial != NO_JAMO) {
				this.medial = NO_JAMO;
			} else {
				this.reset();
			}
		} else {
			this.reset();
		}

		return new Edit(true, this.render());
	}

	/** Commits the visible code point and forgets its editable state. */
	public void commit() {
		this.reset();
	}

	public boolean isComposing() {
		return this.initial != NO_JAMO || this.medial != NO_JAMO;
	}

	private Edit inputConsonant(final int consonant) {
		String before = this.render();
		String committedPrefix = "";

		if (!this.isComposing()) {
			this.initial = consonant;
		} else if (this.initial != NO_JAMO && this.medial == NO_JAMO) {
			committedPrefix = before;
			this.setInitial(consonant);
		} else if (this.initial == NO_JAMO) {
			committedPrefix = before;
			this.setInitial(consonant);
		} else if (this.finalConsonant == 0) {
			int candidate = INITIAL_TO_FINAL[consonant];
			if (candidate == 0) {
				committedPrefix = before;
				this.setInitial(consonant);
			} else {
				this.finalConsonant = candidate;
			}
		} else {
			int candidate = INITIAL_TO_FINAL[consonant];
			int combined = combineFinal(this.finalConsonant, candidate);
			if (combined != 0) {
				this.finalConsonant = combined;
			} else {
				committedPrefix = before;
				this.setInitial(consonant);
			}
		}

		return new Edit(!before.isEmpty(), committedPrefix + this.render());
	}

	private Edit inputVowel(final int vowel) {
		String before = this.render();
		String committedPrefix = "";

		if (!this.isComposing()) {
			this.medial = vowel;
		} else if (this.initial != NO_JAMO && this.medial == NO_JAMO) {
			this.medial = vowel;
		} else if (this.initial == NO_JAMO) {
			int combined = combineVowel(this.medial, vowel);
			if (combined != NO_JAMO) {
				this.rememberMedial();
				this.medial = combined;
			} else {
				committedPrefix = before;
				this.setVowel(vowel);
			}
		} else if (this.finalConsonant == 0) {
			int combined = combineVowel(this.medial, vowel);
			if (combined != NO_JAMO) {
				this.rememberMedial();
				this.medial = combined;
			} else {
				committedPrefix = before;
				this.setVowel(vowel);
			}
		} else {
			int movedFinal = this.finalConsonant;
			int keptFinal = 0;
			int split = splitFinal(this.finalConsonant);
			if (split != 0) {
				keptFinal = split >>> 8;
				movedFinal = split & 0xFF;
			}

			int nextInitial = FINAL_TO_INITIAL[movedFinal];
			if (nextInitial == NO_JAMO) {
				committedPrefix = before;
				this.setVowel(vowel);
			} else {
				committedPrefix = Character.toString(compose(this.initial, this.medial, keptFinal));
				this.initial = nextInitial;
				this.medial = vowel;
				this.finalConsonant = 0;
				this.medialHistorySize = 0;
			}
		}

		return new Edit(!before.isEmpty(), committedPrefix + this.render());
	}

	private String render() {
		if (this.initial != NO_JAMO && this.medial != NO_JAMO) {
			return Character.toString(compose(this.initial, this.medial, this.finalConsonant));
		}

		if (this.initial != NO_JAMO) {
			return Character.toString(INITIAL_GLYPHS[this.initial]);
		}

		if (this.medial != NO_JAMO) {
			return Character.toString(MEDIAL_GLYPHS[this.medial]);
		}

		return "";
	}

	private void setInitial(final int consonant) {
		this.initial = consonant;
		this.medial = NO_JAMO;
		this.finalConsonant = 0;
		this.medialHistorySize = 0;
	}

	private void setVowel(final int vowel) {
		this.initial = NO_JAMO;
		this.medial = vowel;
		this.finalConsonant = 0;
		this.medialHistorySize = 0;
	}

	private void reset() {
		this.initial = NO_JAMO;
		this.medial = NO_JAMO;
		this.finalConsonant = 0;
		this.medialHistorySize = 0;
	}

	private void rememberMedial() {
		if (this.medialHistorySize < this.medialHistory.length) {
			this.medialHistory[this.medialHistorySize++] = this.medial;
		}
	}

	private static char compose(final int initial, final int medial, final int finalConsonant) {
		return (char)(0xAC00 + ((initial * 21) + medial) * 28 + finalConsonant);
	}

	private static int combineVowel(final int first, final int second) {
		return switch (first) {
			case 8 -> switch (second) {
				case 0 -> 9;
				case 1 -> 10;
				case 20 -> 11;
				default -> NO_JAMO;
			};
			case 9 -> second == 20 ? 10 : NO_JAMO;
			case 13 -> switch (second) {
				case 4 -> 14;
				case 5 -> 15;
				case 20 -> 16;
				default -> NO_JAMO;
			};
			case 14 -> second == 20 ? 15 : NO_JAMO;
			case 18 -> second == 20 ? 19 : NO_JAMO;
			default -> NO_JAMO;
		};
	}

	private static int combineFinal(final int first, final int second) {
		return switch (first) {
			case 1 -> second == 19 ? 3 : 0;
			case 4 -> second == 22 ? 5 : second == 27 ? 6 : 0;
			case 8 -> switch (second) {
				case 1 -> 9;
				case 16 -> 10;
				case 17 -> 11;
				case 19 -> 12;
				case 25 -> 13;
				case 26 -> 14;
				case 27 -> 15;
				default -> 0;
			};
			case 17 -> second == 19 ? 18 : 0;
			default -> 0;
		};
	}

	private static int splitFinal(final int finalConsonant) {
		return switch (finalConsonant) {
			case 3 -> 1 << 8 | 19;
			case 5 -> 4 << 8 | 22;
			case 6 -> 4 << 8 | 27;
			case 9 -> 8 << 8 | 1;
			case 10 -> 8 << 8 | 16;
			case 11 -> 8 << 8 | 17;
			case 12 -> 8 << 8 | 19;
			case 13 -> 8 << 8 | 25;
			case 14 -> 8 << 8 | 26;
			case 15 -> 8 << 8 | 27;
			case 18 -> 17 << 8 | 19;
			default -> 0;
		};
	}

	public record Edit(boolean replacePrevious, String text) {
	}
}
