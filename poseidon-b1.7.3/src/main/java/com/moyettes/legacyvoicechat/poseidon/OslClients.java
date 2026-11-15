package com.moyettes.legacyvoicechat.poseidon;

import net.minecraft.server.NetworkManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Which connections are OSL clients, and the channels each announced in its OSL handshake. */
final class OslClients {

	private static final Map<NetworkManager, Boolean> OSL = Collections.synchronizedMap(new WeakHashMap<NetworkManager, Boolean>());
	private static final Map<Integer, Set<String>> CHANNELS = new ConcurrentHashMap<>();

	private OslClients() {
	}

	static void mark(NetworkManager connection) {
		if (connection != null) OSL.put(connection, Boolean.TRUE);
	}

	static boolean isOsl(NetworkManager connection) {
		return connection != null && OSL.containsKey(connection);
	}

	static void setChannels(int playerId, Set<String> channels) {
		CHANNELS.put(playerId, channels);
	}

	static Set<String> channels(int playerId) {
		Set<String> channels = CHANNELS.get(playerId);
		return channels != null ? channels : Collections.<String>emptySet();
	}

	static void forget(int playerId) {
		CHANNELS.remove(playerId);
	}

	static void clear() {
		OSL.clear();
		CHANNELS.clear();
	}
}
