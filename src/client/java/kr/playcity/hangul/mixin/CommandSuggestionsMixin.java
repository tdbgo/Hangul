package kr.playcity.hangul.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import kr.playcity.hangul.CommandSuggestionRecovery;
import kr.playcity.hangul.KoreanSearch;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/** Recovers command literals typed while the Korean keyboard layout was active. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Shadow @Final private EditBox input;

	@Redirect(
		method = "updateCommandInfo",
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
}
