package kr.playcity.hangul.mixin;

import kr.playcity.hangul.InlinePreedit;
import kr.playcity.hangul.NativeImeSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renders OS IME composition inside book pages and every other multiline editor. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin {
	@Shadow @Final private MultilineTextField textField;
	@Shadow private IMEPreeditOverlay preeditOverlay;

	@Unique private PreeditEvent hangul$preedit;
	@Unique private int hangul$preeditStart;
	@Unique private int hangul$preeditEnd;
	@Unique private boolean hangul$renderInjected;
	@Unique private String hangul$savedValue;
	@Unique private int hangul$savedCursor;
	@Unique private int hangul$savedSelection;

	@Inject(method = "preeditUpdated", at = @At("HEAD"))
	private void hangul$onPreedit(
		final PreeditEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (event == null || event.fullText().isEmpty()) {
			hangul$clearPreedit();
			return;
		}

		if (hangul$preedit == null || !hangul$hasValidRange()) {
			MultilineTextFieldAccessor fields = (MultilineTextFieldAccessor) textField;
			hangul$preeditStart = Math.min(
				fields.hangul$getCursor(),
				fields.hangul$getSelectCursor()
			);
			hangul$preeditEnd = Math.max(
				fields.hangul$getCursor(),
				fields.hangul$getSelectCursor()
			);
		}
		hangul$preedit = event;
	}

	@Inject(method = "charTyped", at = @At("HEAD"))
	private void hangul$onCharacter(
		final CharacterEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		hangul$clearPreedit();
	}

	@Inject(method = "setFocused", at = @At("HEAD"))
	private void hangul$onFocusChanged(final boolean focused, final CallbackInfo callback) {
		if (!focused) {
			hangul$clearPreedit();
		}
	}

	@Inject(method = "setValue(Ljava/lang/String;Z)V", at = @At("HEAD"))
	private void hangul$onValueReplaced(
		final String newValue,
		final boolean allowOverflow,
		final CallbackInfo callback
	) {
		hangul$clearPreedit();
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void hangul$injectInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		if (hangul$preedit == null || !hangul$hasValidRange()) {
			return;
		}

		MultilineTextFieldAccessor fields = (MultilineTextFieldAccessor) textField;
		InlinePreedit.Visual visual = InlinePreedit.merge(
			textField.value(),
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		);

		hangul$savedValue = fields.hangul$getValue();
		hangul$savedCursor = fields.hangul$getCursor();
		hangul$savedSelection = fields.hangul$getSelectCursor();
		fields.hangul$setValue(visual.value());
		fields.hangul$setCursor(visual.cursor());
		fields.hangul$setSelectCursor(visual.cursor());
		fields.hangul$reflowDisplayLines();
		hangul$renderInjected = true;
	}

	@Inject(method = "extractContents", at = @At("RETURN"))
	private void hangul$restoreAfterInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		if (!hangul$renderInjected) {
			return;
		}

		MultilineTextFieldAccessor fields = (MultilineTextFieldAccessor) textField;
		fields.hangul$setValue(hangul$savedValue);
		fields.hangul$setCursor(hangul$savedCursor);
		fields.hangul$setSelectCursor(hangul$savedSelection);
		fields.hangul$reflowDisplayLines();
		hangul$savedValue = null;
		hangul$renderInjected = false;
	}

	@Redirect(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setPreeditOverlay(Lnet/minecraft/client/gui/components/Renderable;)V"
		)
	)
	private void hangul$positionCandidateWithoutPopup(
		final GuiGraphicsExtractor graphics,
		final Renderable renderable
	) {
		IMEPreeditOverlayAccessor overlay = (IMEPreeditOverlayAccessor) renderable;
		NativeImeSupport.positionCandidateWindow(
			overlay.hangul$getInputLeft(),
			overlay.hangul$getInputTop(),
			overlay.hangul$getInputHeight()
		);
	}

	@Unique
	private boolean hangul$hasValidRange() {
		return hangul$preeditStart >= 0
			&& hangul$preeditStart <= hangul$preeditEnd
			&& hangul$preeditEnd <= textField.value().length();
	}

	@Unique
	private void hangul$clearPreedit() {
		hangul$preedit = null;
		hangul$preeditStart = 0;
		hangul$preeditEnd = 0;
		preeditOverlay = null;
	}
}
