package kr.playcity.hangul;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;

/** Resolves the newer command-result API once, only when that input path is used. */
public final class CommandResultCompat {
	private CommandResultCompat() {}

	@SuppressWarnings("unchecked")
	public static CompletableFuture<Suggestions> suggestions(final Object result, final int cursor, final String input) {
		try {
			CompletableFuture<Suggestions> vanilla = (CompletableFuture<Suggestions>) Bridge.COMPUTE.invokeExact(result, cursor);
			if (!KoreanSearch.containsHangul(input)) return vanilla;
			ParseResults<?> parse = (ParseResults<?>) Bridge.PARSE.invokeExact(result);
			return vanilla.thenApply(suggestions -> CommandSuggestionRecovery.augment(input, parse, cursor, suggestions));
		} catch (Throwable failure) {
			throw new IllegalStateException("Could not invoke the command suggestion result", failure);
		}
	}

	private static final class Bridge {
		private static final MethodHandle COMPUTE;
		private static final MethodHandle PARSE;

		static {
			try {
				Class<?> type = Class.forName("net.minecraft.commands.SuggestionBox$Result", false, CommandResultCompat.class.getClassLoader());
				var lookup = MethodHandles.publicLookup();
				COMPUTE = lookup.findVirtual(type, "computeSuggestions", MethodType.methodType(CompletableFuture.class, int.class))
					.asType(MethodType.methodType(CompletableFuture.class, Object.class, int.class));
				PARSE = lookup.findVirtual(type, "parseResults", MethodType.methodType(ParseResults.class))
					.asType(MethodType.methodType(ParseResults.class, Object.class));
			} catch (ReflectiveOperationException failure) {
				throw new ExceptionInInitializerError(failure);
			}
		}
	}
}
