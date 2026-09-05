package kr.playcity.hangul.mixin;

import kr.playcity.hangul.FontLayoutRevision;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@Inject(method = "apply", at = @At("RETURN"))
	private void hangul$invalidateFontLayouts(final CallbackInfo callback) {
		FontLayoutRevision.invalidate();
	}
}
