package com.moyettes.legacyvoicechat.client.bridge;

import com.moyettes.legacyvoicechat.client.platform.IRenderApi;
import com.moyettes.legacyvoicechat.client.platform.IScreenLifecycle;
import net.minecraft.client.gui.screen.Screen;

public final class VoiceBridgeScreen extends Screen {

	private final IScreenLifecycle lifecycle;
	private final IRenderApi api;

	public VoiceBridgeScreen(IScreenLifecycle lifecycle, IRenderApi api) {
		this.lifecycle = lifecycle;
		this.api = api;
	}

	public IScreenLifecycle getLifecycle() {
		return lifecycle;
	}

	@Override
	public void init() {
		super.init();
		lifecycle.init(this.width, this.height);
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		lifecycle.renderBackground(api, mouseX, mouseY, tickDelta);
		lifecycle.renderForeground(api, mouseX, mouseY, tickDelta);
		super.render(mouseX, mouseY, tickDelta);
	}

	@Override
	public void tick() {
		lifecycle.tick();
		super.tick();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (!lifecycle.mouseClicked(mouseX, mouseY, button)) {
			super.mouseClicked(mouseX, mouseY, button);
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		lifecycle.mouseReleased(mouseX, mouseY, button);
		super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected void keyPressed(char chr, int key) {
		boolean handled = false;
		if (key != 0) {
			handled = lifecycle.keyPressed(toGlfwKey(key), key, 0);
		}
		if (!handled && chr != 0) {
			handled = lifecycle.charTyped(chr, 0);
		}
		if (!handled) {
			super.keyPressed(chr, key);
		}
	}

	@Override
	public void removed() {
		lifecycle.close();
		super.removed();
	}

	/**
	 * The shared screens use GLFW key codes (Esc 256, Enter 257, Tab 258, Backspace 259); this version's LWJGL2 sends its
	 * own (1, 28, 15, 14), so Enter, Tab and Esc did nothing in the group screens.
	 */
	private static int toGlfwKey(int lwjgl2Key) {
		switch (lwjgl2Key) {
			case 1: return 256;
			case 28: case 156: return 257;
			case 15: return 258;
			case 14: return 259;
			default: return lwjgl2Key;
		}
	}
}
