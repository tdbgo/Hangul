package kr.playcity.hangul.mixin;

import kr.playcity.hangul.InlinePreedit;
import kr.playcity.hangul.NativeImeSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
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

/** Adds inline OS IME composition and native candidate positioning to sign editors. */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {
	@Shadow @Final private String[] messages;
	@Shadow private int line;
	@Shadow private TextFieldHelper signField;
	@Shadow private IMEPreeditOverlay preeditOverlay;

	@Unique private PreeditEvent hangul$preedit;
	@Unique private int hangul$preeditStart;
	@Unique private int hangul$preeditEnd;
	@Unique private int hangul$preeditLine = -1;
	@Unique private boolean hangul$renderInjected;
	@Unique private int hangul$savedLine;
	@Unique private String hangul$savedMessage;
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
			hangul$preeditLine = line;
			hangul$preeditStart = Math.min(signField.getCursorPos(), signField.getSelectionPos());
			hangul$preeditEnd = Math.max(signField.getCursorPos(), signField.getSelectionPos());
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

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void hangul$injectInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		if (hangul$preedit != null && line != hangul$preeditLine) {
			hangul$clearPreedit();
		}
		if (hangul$preedit == null || !hangul$hasValidRange()) {
			return;
		}

		InlinePreedit.Visual visual = InlinePreedit.merge(
			messages[line],
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		);
		hangul$savedLine = line;
		hangul$savedMessage = messages[line];
		hangul$savedCursor = signField.getCursorPos();
		hangul$savedSelection = signField.getSelectionPos();
		messages[line] = visual.value();
		signField.setCursorPos(visual.cursor(), false);
		signField.setSelectionPos(visual.cursor());
		hangul$renderInjected = true;
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
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

		messages[hangul$savedLine] = hangul$savedMessage;
		signField.setCursorPos(hangul$savedCursor, false);
		signField.setSelectionPos(hangul$savedSelection);
		hangul$savedMessage = null;
		hangul$renderInjected = false;
	}

	@Redirect(
		method = "extractSign",
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
		return line == hangul$preeditLine
			&& line >= 0 && line < messages.length
			&& hangul$preeditStart >= 0
			&& hangul$preeditStart <= hangul$preeditEnd
			&& hangul$preeditEnd <= messages[line].length();
	}

	@Unique
	private void hangul$clearPreedit() {
		hangul$preedit = null;
		hangul$preeditStart = 0;
		hangul$preeditEnd = 0;
		hangul$preeditLine = -1;
		preeditOverlay = null;
	}
}
