package com.moyettes.legacyvoicechat.poseidon;

import net.minecraft.server.Packet;
import net.minecraft.server.Packet2Handshake;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Puts packet 250 and the OSL-aware handshake into Poseidon's packet tables, and takes them out again on disable, so
 * {@code /reload} works (a reloaded plugin has new classes; the old ones must not stay registered).
 */
final class PacketRegistry {

	private PacketRegistry() {
	}

	@SuppressWarnings("unchecked")
	private static <T> T table(String name) throws ReflectiveOperationException {
		Field field = Packet.class.getDeclaredField(name);
		field.setAccessible(true);
		return (T) field.get(null);
	}

	/** False when another plugin already owns packet 250 (then voice can't work for OSL clients). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static boolean install() throws ReflectiveOperationException {
		Map idToClass = table("packetIdToClassMap");
		Map classToId = table("packetClassToIdMap");
		Set clientIds = table("clientPacketIdList");
		Set serverIds = table("serverPacketIdList");
		synchronized (Packet.class) {
			Object current = idToClass.get(CustomPayloadPacket.ID);
			if (current != null && !((Class<?>) current).getName().equals(CustomPayloadPacket.class.getName())) {
				return false;
			}
			removeStale(idToClass, classToId);
			idToClass.put(CustomPayloadPacket.ID, CustomPayloadPacket.class);
			classToId.put(CustomPayloadPacket.class, CustomPayloadPacket.ID);
			clientIds.add(CustomPayloadPacket.ID);
			serverIds.add(CustomPayloadPacket.ID);
			// Reading packet 2 creates our subclass; the server's own handshakes (Packet2Handshake) still map to 2.
			idToClass.put(2, OslHandshakePacket.class);
			classToId.put(OslHandshakePacket.class, 2);
		}
		return true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static void uninstall() {
		try {
			Map idToClass = table("packetIdToClassMap");
			Map classToId = table("packetClassToIdMap");
			Set clientIds = table("clientPacketIdList");
			Set serverIds = table("serverPacketIdList");
			synchronized (Packet.class) {
				if (idToClass.get(CustomPayloadPacket.ID) == CustomPayloadPacket.class) {
					idToClass.remove(CustomPayloadPacket.ID);
					clientIds.remove(CustomPayloadPacket.ID);
					serverIds.remove(CustomPayloadPacket.ID);
				}
				classToId.remove(CustomPayloadPacket.class);
				if (idToClass.get(2) == OslHandshakePacket.class) {
					idToClass.put(2, Packet2Handshake.class);
				}
				classToId.remove(OslHandshakePacket.class);
			}
		} catch (ReflectiveOperationException e) {
			// nothing was installed
		}
	}

	/** Entries of an earlier load of this plugin (same names, another class loader). */
	@SuppressWarnings({"rawtypes"})
	private static void removeStale(Map idToClass, Map classToId) {
		classToId.keySet().removeIf(c -> {
			String name = ((Class<?>) c).getName();
			return (name.equals(CustomPayloadPacket.class.getName()) && c != CustomPayloadPacket.class)
				|| (name.equals(OslHandshakePacket.class.getName()) && c != OslHandshakePacket.class);
		});
		idToClass.remove(CustomPayloadPacket.ID);
	}
}
