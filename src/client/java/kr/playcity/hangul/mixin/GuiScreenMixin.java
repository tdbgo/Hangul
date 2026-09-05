package kr.playcity.hangul.mixin;

import kr.playcity.hangul.HangulController;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
	// Screen ownership moved from Minecraft to Gui in 26.2.
	@Inject(method = "setScreen", at = @At("HEAD"), require = 0, expect = 0)
	private void hangul$endFallbackSession(final CallbackInfo callback) {
		HangulController.onScreenChanged();
	}
}
