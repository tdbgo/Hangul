package kr.playcity.hangul;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Safely augments Brigadier literals after an accidental Korean-layout input. */
public final class CommandSuggestionRecovery {
	private CommandSuggestionRecovery() {
	}

	public static <S> Suggestions augment(
		final String input,
		final ParseResults<S> parse,
		final int cursor,
		final Suggestions vanilla
	) {
		if (input == null || parse == null || vanilla == null || cursor < 0 || cursor > input.length()) {
			return vanilla;
		}

		try {
			SuggestionContext<S> context = parse.getContext().findSuggestionContext(cursor);
			int start = context.startPos;
			if (start < 0 || start > cursor) {
				return vanilla;
			}

			String koreanQuery = input.substring(start, cursor);
			if (!KoreanSearch.containsHangul(koreanQuery)) {
				return vanilla;
			}

			String latinQuery = KoreanSearch.toLatinKeys(koreanQuery).toLowerCase(Locale.ROOT);
			String phoneticQuery = KoreanSearch.koreanPhoneticKey(koreanQuery);
			if (latinQuery.isEmpty() && phoneticQuery.length() < 2) {
				return vanilla;
			}

			Set<String> existing = new HashSet<>(vanilla.getList().size());
			for (Suggestion suggestion : vanilla.getList()) {
				existing.add(suggestion.getText().toLowerCase(Locale.ROOT));
			}
			List<Suggestion> recovered = null;
			S source = parse.getContext().getSource();
			StringRange range = StringRange.between(start, cursor);

			for (CommandNode<S> node : context.parent.getChildren()) {
				if (!(node instanceof LiteralCommandNode<S>) || !node.canUse(source)) {
					continue;
				}
				String literal = node.getName();
				String normalizedLiteral = literal.toLowerCase(Locale.ROOT);
				boolean layoutMatch = !latinQuery.isBlank() && normalizedLiteral.startsWith(latinQuery);
				boolean phoneticMatch = phoneticQuery.length() >= 2
					&& KoreanSearch.latinPhoneticKey(normalizedLiteral).startsWith(phoneticQuery);
				if ((layoutMatch || phoneticMatch) && existing.add(normalizedLiteral)) {
					if (recovered == null) {
						recovered = new ArrayList<>();
					}
					recovered.add(new Suggestion(range, literal));
				}
			}

			if (recovered == null) {
				return vanilla;
			}
			Suggestions additions = new Suggestions(range, recovered);
			return Suggestions.merge(input, List.of(vanilla, additions));
		} catch (RuntimeException ignored) {
			// Suggestions are convenience only. Mapping drift or an unusual parser
			// state must never break the chat screen.
			return vanilla;
		}
	}
}
