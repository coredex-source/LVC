package com.moyettes.legacyvoicechat.poseidon;

import net.minecraft.server.EntityPlayer;

/** Hands packet 250 payloads (main thread) to the enabled plugin. */
public final class PayloadRouter {

	interface Handler {
		void handle(EntityPlayer player, String channel, byte[] data);
	}

	private static volatile Handler handler;

	private PayloadRouter() {
	}

	static void set(Handler h) {
		handler = h;
	}

	static void handle(EntityPlayer player, String channel, byte[] data) {
		Handler h = handler;
		if (h != null && player != null && channel != null && data != null) {
			h.handle(player, channel, data);
		}
	}
}
