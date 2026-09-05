package kr.playcity.hangul.mixin;

import kr.playcity.hangul.PreeditValueProvider;
import kr.playcity.hangul.KoreanSearch;
import java.util.List;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes creative search react to the current IME preedit without storing it. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	@Unique private String hangul$rawSearch = "";
	@Shadow private void refreshSearchResults() {
	}

	@Inject(method = "preeditUpdated", at = @At("RETURN"))
	private void hangul$refreshDuringPreedit(
		final PreeditEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (callback.getReturnValue()) {
			refreshSearchResults();
		}
	}

	@Redirect(
		method = "refreshSearchResults",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;")
	)
	private String hangul$searchVisualPreedit(final EditBox searchBox) {
		hangul$rawSearch = ((PreeditValueProvider) searchBox).hangul$getVisualValue();
		return hangul$rawSearch;
	}

	@Redirect(method = "refreshSearchResults", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/searchtree/SearchTree;search(Ljava/lang/String;)Ljava/util/List;"))
	private <T> List<T> hangul$searchOriginalKeys(final SearchTree<T> tree, final String lowercaseQuery) {
		// Tag queries keep their vanilla syntax and identifier lookup behavior.
		return hangul$rawSearch.startsWith("#") ? tree.search(lowercaseQuery)
			: KoreanSearch.search(tree::search, hangul$rawSearch);
	}
}
