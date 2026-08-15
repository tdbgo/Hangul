package kr.playcity.hangul;

/** Read-only view used by search UIs without mutating or transmitting preedit. */
public interface PreeditValueProvider {
	String hangul$getVisualValue();
}
