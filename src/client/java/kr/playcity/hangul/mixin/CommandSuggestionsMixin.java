package kr.playcity.hangul.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import kr.playcity.hangul.CommandSuggestionRecovery;
import kr.playcity.hangul.CommandResultCompat;
import kr.playcity.hangul.KoreanSearch;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/** Recovers command literals typed while the Korean keyboard layout was active. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Shadow @Final private EditBox input;

	@Group(name = "hangulCommandSuggestions", min = 1, max = 1)
	@Redirect(
		method = "updateCommandInfo",
		require = 0, expect = 0,
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;",
			remap = false
		)
	)
	private CompletableFuture<Suggestions> hangul$recoverKoreanLayoutCommand(
		final CommandDispatcher<ClientSuggestionProvider> dispatcher,
		final ParseResults<ClientSuggestionProvider> parse,
		final int cursor
	) {
		String value = input.getValue();
		CompletableFuture<Suggestions> vanilla = dispatcher.getCompletionSuggestions(parse, cursor);
		if (!KoreanSearch.containsHangul(value)) {
			return vanilla;
		}
		return vanilla.thenApply(
			suggestions -> CommandSuggestionRecovery.augment(value, parse, cursor, suggestions)
		);
	}

	@Group(name = "hangulCommandSuggestions", min = 1, max = 1)
	@Redirect(method = "updateCommandInfo", require = 0, expect = 0,
		at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraft/commands/SuggestionBox$Result;computeSuggestions(I)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<Suggestions> hangul$recoverResultCommand(@Coerce final Object result, final int cursor) {
		return CommandResultCompat.suggestions(result, cursor, input.getValue());
	}
}
