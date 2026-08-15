package kr.playcity.hangul.mixin;

import kr.playcity.hangul.PreeditValueProvider;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes recipe search react to the current IME preedit without storing it. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
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
		return ((PreeditValueProvider) searchBox).hangul$getVisualValue();
	}
}
