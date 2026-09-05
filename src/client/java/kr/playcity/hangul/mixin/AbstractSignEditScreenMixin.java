package kr.playcity.hangul.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kr.playcity.hangul.PreeditCache;
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
	@Unique private final PreeditCache hangul$visualCache = new PreeditCache();

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

	@WrapMethod(method = "extractRenderState")
	private void hangul$renderInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final Operation<Void> original
	) {
		if (hangul$preedit != null && line != hangul$preeditLine) {
			hangul$clearPreedit();
		}
		if (hangul$preedit == null || !hangul$hasValidRange()) {
			original.call(graphics, mouseX, mouseY, partialTick);
			return;
		}

		InlinePreedit.Visual visual = hangul$visualCache.get(
			messages[line],
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		);
		int savedLine = line;
		String savedMessage = messages[line];
		int savedCursor = signField.getCursorPos();
		int savedSelection = signField.getSelectionPos();
		try {
			messages[line] = visual.value();
			signField.setCursorPos(visual.cursor(), false);
			signField.setSelectionPos(visual.cursor());
			original.call(graphics, mouseX, mouseY, partialTick);
		} finally {
			messages[savedLine] = savedMessage;
			signField.setCursorPos(savedCursor, false);
			signField.setSelectionPos(savedSelection);
		}
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
		hangul$visualCache.clear();
		hangul$preedit = null;
		hangul$preeditStart = 0;
		hangul$preeditEnd = 0;
		hangul$preeditLine = -1;
		preeditOverlay = null;
	}
}
