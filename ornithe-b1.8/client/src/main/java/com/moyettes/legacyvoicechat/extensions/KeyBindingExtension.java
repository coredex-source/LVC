package com.moyettes.legacyvoicechat.extensions;

public interface KeyBindingExtension {
	/** Edge-triggered press, LVC's own (vanilla's isPressed() has the same name from 1.8 on: it was overwritten). */
	boolean lvc$wasPressed();
}
