package kr.playcity.hangul.mixin;

import kr.playcity.hangul.PreeditValueProvider;
import kr.playcity.hangul.KoreanSearch;
import java.util.List;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes recipe search react to the current IME preedit without storing it. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
	@Unique private String hangul$rawSearch = "";
	@Unique private String hangul$lastRawSearch = "";
	@Shadow private void checkSearchStringUpdate() {
	}

	@Inject(method = "preeditUpdated", at = @At("RETURN"))
	private void hangul$refreshDuringPreedit(
		final PreeditEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (callback.getReturnValue()) {
			checkSearchStringUpdate();
		}
	}

	@Redirect(
		method = {"checkSearchStringUpdate", "updateCollections"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;")
	)
	private String hangul$searchVisualPreedit(final EditBox searchBox) {
		hangul$rawSearch = ((PreeditValueProvider) searchBox).hangul$getVisualValue();
		return hangul$rawSearch;
	}

	@Redirect(method = "updateCollections", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/searchtree/SearchTree;search(Ljava/lang/String;)Ljava/util/List;"))
	private <T> List<T> hangul$searchOriginalKeys(final SearchTree<T> tree, final String lowercaseQuery) {
		return KoreanSearch.search(tree::search, hangul$rawSearch);
	}

	@Redirect(method = "checkSearchStringUpdate", at = @At(value = "INVOKE",
		target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
	private boolean hangul$compareOriginalKeys(final String lowercaseQuery, final Object previousQuery) {
		boolean unchanged = lowercaseQuery.equals(previousQuery) && hangul$rawSearch.equals(hangul$lastRawSearch);
		hangul$lastRawSearch = hangul$rawSearch;
		return unchanged;
	}
}
