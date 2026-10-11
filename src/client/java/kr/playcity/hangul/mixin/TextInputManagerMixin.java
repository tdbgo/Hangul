package kr.playcity.hangul.mixin;

import com.mojang.blaze3d.platform.TextInputManager;
import com.mojang.blaze3d.platform.Window;
import kr.playcity.hangul.ImeWidthController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextInputManager.class)
public abstract class TextInputManagerMixin {
	@Shadow @Final private Window window;
	@Shadow private boolean textInputEnabled;

	// 26.3 adds an owner argument and stop overload; inspect the actual resulting state.
	@Inject(method = {"startTextInput", "stopTextInput"}, at = @At("TAIL"))
	private void hangul$onInputStateChanged(CallbackInfo callback) {
		ImeWidthController.onTextInputChanged(window.handle(), textInputEnabled);
	}
}
