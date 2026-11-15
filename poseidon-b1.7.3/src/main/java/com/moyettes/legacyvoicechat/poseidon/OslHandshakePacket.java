package com.moyettes.legacyvoicechat.poseidon;

import net.minecraft.server.NetHandler;
import net.minecraft.server.NetLoginHandler;
import net.minecraft.server.Packet2Handshake;

/**
 * Packet 2 (handshake), noting the connections of OSL clients: OSL sends {@code "\0OrnitheMC"} as the handshake string
 * (Poseidon ignores that string). Only those clients understand packet 250; sending it to anyone else would kick them.
 */
public class OslHandshakePacket extends Packet2Handshake {

	public static final String OSL_KEY = "\u0000OrnitheMC";

	@Override
	public void a(NetHandler handler) {
		if (a != null && a.endsWith(OSL_KEY) && handler instanceof NetLoginHandler) {
			OslClients.mark(((NetLoginHandler) handler).networkManager);
		}
		super.a(handler);
	}
}
