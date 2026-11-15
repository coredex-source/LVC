package com.moyettes.legacyvoicechat.network;

import com.moyettes.legacyvoicechat.packet.CustomPayload;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.networking.api.ChannelIdentifiers;
import net.ornithemc.osl.networking.api.ChannelRegistry;
import net.ornithemc.osl.networking.api.PacketBuffer;
import net.ornithemc.osl.networking.api.PacketPayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One of LVC's payloads (common/packet, written with Data streams) carried as an OSL gen2 payload, byte for byte, so
 * the bytes on the wire stay what every LVC server sends and expects.
 */
public final class OslPayload<P extends CustomPayload> implements PacketPayload {

	/** LVC's channels, kept in their legacy "voice|..." form. */
	public static final NamespacedIdentifier MOD_SUPPORTED = ChannelRegistry.register(ChannelIdentifiers.legacy("voice", "mod_supported"));
	public static final NamespacedIdentifier SERVER_INFO = ChannelRegistry.register(ChannelIdentifiers.legacy("voice", "server_info"));

	private final P payload;

	public OslPayload(P payload) {
		this.payload = payload;
	}

	public P get() {
		return payload;
	}

	@Override
	public void read(PacketBuffer buffer) throws IOException {
		// Leaves the buffer unread: from 14w21a on, a listener that calls ensureOnMainThread() is run again on the
		// main thread with this same buffer, and that second read would otherwise find it empty.
		byte[] bytes = new byte[buffer.readableBytes()];
		buffer.getBytes(buffer.readerIndex(), bytes);
		payload.read(new DataInputStream(new ByteArrayInputStream(bytes)));
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		payload.write(new DataOutputStream(out));
		buffer.writeBytes(out.toByteArray());
	}
}
