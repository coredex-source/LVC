package com.moyettes.legacyvoicechat.poseidon;

import net.minecraft.server.NetHandler;
import net.minecraft.server.NetServerHandler;
import net.minecraft.server.Packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Packet 250 as OSL sends it on Beta 1.x (which has no custom payload packet of its own):
 * {@code [channel: writeString, max 255][short length][bytes, max 32767]}.
 */
public class CustomPayloadPacket extends Packet {

	public static final int ID = 250;
	private static final int MAX_SIZE = 32767;

	public String channel;
	public byte[] data;

	public CustomPayloadPacket() {
	}

	public CustomPayloadPacket(String channel, byte[] data) {
		if (data.length > MAX_SIZE) throw new IllegalArgumentException("Payload may not be larger than 32k");
		this.channel = channel;
		this.data = data;
	}

	@Override
	public void a(DataInputStream in) throws IOException {
		channel = Packet.a(in, 255);
		int size = in.readShort();
		if (size < 0 || size > MAX_SIZE) throw new IOException("Payload may not be larger than 32k");
		data = new byte[size];
		in.readFully(data);
	}

	@Override
	public void a(DataOutputStream out) throws IOException {
		Packet.a(channel, out);
		out.writeShort(data.length);
		out.write(data);
	}

	@Override
	public void a(NetHandler handler) {
		// Packets are handled on the main thread. Before login (NetLoginHandler) there is no player yet: ignored.
		if (handler instanceof NetServerHandler) {
			PayloadRouter.handle(((NetServerHandler) handler).player, channel, data);
		}
	}

	@Override
	public int a() {
		return 2 + channel.length() * 2 + 2 + data.length;
	}
}
