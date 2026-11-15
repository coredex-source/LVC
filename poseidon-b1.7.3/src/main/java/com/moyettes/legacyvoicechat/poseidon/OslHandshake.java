package com.moyettes.legacyvoicechat.poseidon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OSL gen2's channel handshake (channel {@code osl:handshake}): {@code byte protocol (1)}, {@code int count}, then
 * {@code count} channel names as VarInt-length UTF-8. The server sends the channels it listens on; the client answers
 * with its own and only then sends anything (OSL drops sends on channels the server didn't announce).
 */
final class OslHandshake {

	static final String CHANNEL = "osl:handshake";
	static final byte PROTOCOL = 1;

	private OslHandshake() {
	}

	static byte[] encode(Collection<String> channels) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(PROTOCOL);
		out.writeInt(channels.size());
		for (String channel : channels) {
			byte[] utf8 = channel.getBytes(StandardCharsets.UTF_8);
			writeVarInt(out, utf8.length);
			out.write(utf8);
		}
		return bytes.toByteArray();
	}

	static Set<String> decodeChannels(byte[] data) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		in.readByte(); // protocol
		int count = in.readInt();
		if (count < 0 || count > 1024) throw new IOException("bad channel count " + count);
		Set<String> channels = new LinkedHashSet<>();
		for (int i = 0; i < count; i++) {
			int length = readVarInt(in);
			if (length < 0 || length > 32767 * 3) throw new IOException("bad channel name length " + length);
			byte[] utf8 = new byte[length];
			in.readFully(utf8);
			channels.add(new String(utf8, StandardCharsets.UTF_8));
		}
		return channels;
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0, shift = 0;
		byte b;
		do {
			if (shift >= 35) throw new IOException("VarInt too big");
			b = in.readByte();
			value |= (b & 0x7F) << shift;
			shift += 7;
		} while ((b & 0x80) != 0);
		return value;
	}
}
