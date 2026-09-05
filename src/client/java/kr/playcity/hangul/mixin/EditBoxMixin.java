package kr.playcity.hangul.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kr.playcity.hangul.HangulController;
import kr.playcity.hangul.InlinePreedit;
import kr.playcity.hangul.NativeImeSupport;
import kr.playcity.hangul.PreeditValueProvider;
import kr.playcity.hangul.PreeditCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds inline OS IME preedit rendering and an emergency physical-key fallback. */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements PreeditValueProvider {
	@Shadow @Final private Font font;
	@Shadow private String value;
	@Shadow private int displayPos;
	@Shadow private int cursorPos;
	@Shadow private int highlightPos;
	@Shadow private int textX;
	@Shadow private int textY;

	@Unique private PreeditEvent hangul$preedit;
	@Unique private int hangul$preeditStart;
	@Unique private int hangul$preeditEnd;
	@Unique private final PreeditCache hangul$visualCache = new PreeditCache();
	@Unique private int hangul$renderCompositionStart;
	@Unique private int hangul$renderCompositionEnd;

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void hangul$onKeyPress(final KeyEvent event, final CallbackInfoReturnable<Boolean> callback) {
		if (HangulController.onEditBoxKeyPress((EditBox) (Object) this, event)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void hangul$onCharacter(final CharacterEvent event, final CallbackInfoReturnable<Boolean> callback) {
		hangul$clearPreedit();
		if (HangulController.onEditBoxCharacter((EditBox) (Object) this, event)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
	private void hangul$onPreedit(final PreeditEvent event, final CallbackInfoReturnable<Boolean> callback) {
		if (HangulController.isForcedHangulMode((EditBox) (Object) this)
			|| event == null || event.fullText().isEmpty()) {
			hangul$clearPreedit();
		} else {
			if (hangul$preedit == null || !hangul$hasValidRange()) {
				hangul$preeditStart = Math.min(cursorPos, highlightPos);
				hangul$preeditEnd = Math.max(cursorPos, highlightPos);
			}
			hangul$preedit = event;
		}

		// Replace Minecraft's detached popup with the inline render below.
		callback.setReturnValue(true);
	}

	@Inject(method = "setFocused", at = @At("HEAD"))
	private void hangul$onFocusChanged(final boolean focused, final CallbackInfo callback) {
		if (!focused) {
			hangul$clearPreedit();
			HangulController.onFocusLost((EditBox) (Object) this);
		}
	}

	@Inject(method = "setValue", at = @At("HEAD"))
	private void hangul$onValueReplaced(final String newValue, final CallbackInfo callback) {
		hangul$clearPreedit();
	}

	@WrapMethod(method = "extractWidgetRenderState")
	private void hangul$renderInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final Operation<Void> original
	) {
		EditBox box = (EditBox) (Object) this;
		if (hangul$preedit == null || HangulController.isForcedHangulMode(box)
			|| !box.isVisible() || !hangul$hasValidRange()) {
			original.call(graphics, mouseX, mouseY, partialTick);
			return;
		}

		InlinePreedit.Visual visual = hangul$visualCache.get(
			value,
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		);

		String savedValue = value;
		int savedDisplayPos = displayPos;
		int savedCursorPos = cursorPos;
		int savedHighlightPos = highlightPos;
		try {
			value = visual.value();
			cursorPos = visual.cursor();
			highlightPos = cursorPos;
			hangul$renderCompositionStart = visual.compositionStart();
			hangul$renderCompositionEnd = visual.compositionEnd();
			hangul$scrollVisualCursor(box);
			original.call(graphics, mouseX, mouseY, partialTick);
			hangul$drawCompositionUnderline(graphics, box);
		} finally {
			value = savedValue;
			displayPos = savedDisplayPos;
			cursorPos = savedCursorPos;
			highlightPos = savedHighlightPos;
		}
	}

	@Unique
	@Override
	public String hangul$getVisualValue() {
		if (hangul$preedit == null || HangulController.isForcedHangulMode((EditBox) (Object) this)
			|| !hangul$hasValidRange()) {
			return value;
		}
		return hangul$visualCache.get(
			value,
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		).value();
	}

	@Unique
	private boolean hangul$hasValidRange() {
		return hangul$preeditStart >= 0
			&& hangul$preeditStart <= hangul$preeditEnd
			&& hangul$preeditEnd <= value.length();
	}

	@Unique
	private void hangul$clearPreedit() {
		hangul$visualCache.clear();
		hangul$preedit = null;
		hangul$preeditStart = 0;
		hangul$preeditEnd = 0;
	}

	@Unique
	private void hangul$scrollVisualCursor(final EditBox box) {
		displayPos = Math.max(0, Math.min(displayPos, value.length()));
		String visible = font.plainSubstrByWidth(value.substring(displayPos), box.getInnerWidth());
		int visibleEnd = displayPos + visible.length();
		if (cursorPos > visibleEnd) {
			displayPos += cursorPos - visibleEnd;
		} else if (cursorPos <= displayPos) {
			displayPos = cursorPos;
		}
		displayPos = Math.max(0, Math.min(displayPos, value.length()));
	}

	@Unique
	private void hangul$drawCompositionUnderline(final GuiGraphicsExtractor graphics, final EditBox box) {
		int caretX = textX + font.width(value.substring(displayPos, Math.max(displayPos, cursorPos)));
		int right = box.getX() + box.getWidth() - (box.isBordered() ? 4 : 0);
		NativeImeSupport.positionCandidateWindow(Math.min(caretX, right), textY, 10);

		int visibleStart = Math.max(displayPos, hangul$renderCompositionStart);
		int visibleEnd = Math.max(visibleStart, Math.min(value.length(), hangul$renderCompositionEnd));
		if (visibleStart >= visibleEnd) {
			return;
		}

		int startX = textX + font.width(value.substring(displayPos, visibleStart));
		int endX = startX + font.width(value.substring(visibleStart, visibleEnd));
		endX = Math.min(endX, right);
		if (endX > startX) {
			graphics.fill(startX, textY + 9, endX, textY + 10, 0xFFFFFFFF);
		}
	}
}
