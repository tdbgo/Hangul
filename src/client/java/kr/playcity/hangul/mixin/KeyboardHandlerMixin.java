package kr.playcity.hangul.mixin;

import kr.playcity.hangul.HangulController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void hangul$onKeyPress(final long handle, final int action, final KeyEvent event, final CallbackInfo callback) {
		if (HangulController.onKeyPress(handle, action, event)) {
			callback.cancel();
		}
	}
}
