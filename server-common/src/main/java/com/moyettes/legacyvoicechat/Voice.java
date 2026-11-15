package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.MinecraftCompat;
import com.moyettes.legacyvoicechat.compat.PlayerSnapshot;
import com.moyettes.legacyvoicechat.compat.VoicePlayer;
import com.moyettes.legacyvoicechat.log.VoiceLog;
import com.moyettes.legacyvoicechat.metrics.Metrics;
import com.moyettes.legacyvoicechat.server.VoiceServer;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Voice {

	public static final VoiceLog LOGGER = VoiceLog.get("LegacyVoiceChat");

	private static final SecureRandom RANDOM = new SecureRandom();

	private static VoiceContext context;
	private static VoiceServer voiceServer;
	private static Metrics metrics;
	/** Auth tokens handed out over the game connection, by player network id; the voice server consumes them. */
	private static final Map<Integer, String> playerAuthTokens = new ConcurrentHashMap<>();
	/** The players as of the last game tick (see {@link #onServerTick()}); empty until the first tick. */
	private static volatile Map<Integer, VoicePlayer> players = Collections.emptyMap();
	private static volatile long playersCapturedAt;

	public void init(VoiceContext ctx) {
		if (context != null) {
			throw new IllegalStateException("Voice has already been initialized");
		}
		context = ctx;

		LOGGER.info("Initializing LegacyVoiceChat on {} ({})", ctx.loader, ctx.minecraftVersion);

		try {
			voiceServer = new VoiceServer();
			voiceServer.start();
			LOGGER.info("Voice server listening on UDP {}", voiceServer.getPort());
		} catch (IOException e) {
			LOGGER.error("Failed to start voice server: {}", e.getMessage());
		}

		ctx.net.registerNetworkListeners();
	}

	public static VoiceContext getContext() {
		return context;
	}

	/**
	 * A fresh auth token: 128 random bits. It used to be "token_&lt;name&gt;_&lt;millis&gt;", which anyone could guess;
	 * the name is no longer part of it.
	 */
	public static String generateAuthToken(String playerName) {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		StringBuilder hex = new StringBuilder(32);
		for (byte b : bytes) {
			hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return hex.toString();
	}

	/**
	 * Whether a client announcing {@code protocolVersion} can use this server (same major version); logs when not.
	 * Platforms call this before handing out a token.
	 */
	public static boolean acceptsClient(String playerName, String protocolVersion) {
		if (Protocol.isCompatible(protocolVersion)) return true;
		LOGGER.warn("{} has LegacyVoiceChat protocol {}, this server {}: no voice until one side is updated", playerName,
			protocolVersion, Protocol.VERSION);
		return false;
	}

	public static VoiceServer getVoiceServer() {
		return voiceServer;
	}

	public static String getPlayerAuthToken(Integer playerNetworkId) {
		return playerAuthTokens.get(playerNetworkId);
	}

	public static Map<Integer, String> getPlayerAuthTokens() {
		return playerAuthTokens;
	}

	/**
	 * Call on the game thread once per tick: the voice server reads players and positions from this snapshot instead
	 * of touching the game's player list from its own thread.
	 */
	public static void onServerTick() {
		VoiceContext ctx = context;
		if (ctx == null || ctx.mc == null) return;
		Map<Integer, VoicePlayer> now = new HashMap<>();
		for (VoicePlayer player : ctx.mc.getAllConnectedPlayers()) {
			if (player != null) {
				now.put(player.getNetworkId(), new PlayerSnapshot(player));
			}
		}
		players = Collections.unmodifiableMap(now);
		playersCapturedAt = System.currentTimeMillis();
	}

	/** The players of the last tick, or null when no platform has called {@link #onServerTick()} recently. */
	public static Map<Integer, VoicePlayer> getPlayerSnapshot() {
		return System.currentTimeMillis() - playersCapturedAt < 5000 ? players : null;
	}

	public static int getOnlinePlayerCount() {
		Map<Integer, VoicePlayer> snapshot = getPlayerSnapshot();
		if (snapshot != null) return snapshot.size();
		MinecraftCompat mc = context != null ? context.mc : null;
		return mc != null ? mc.getOnlinePlayerCount() : 0;
	}

	/** Stops the voice server and metrics (the server is shutting down, or the plugin is disabled). */
	public static void shutdown() {
		if (voiceServer != null) {
			voiceServer.stop();
		}
		if (metrics != null) {
			metrics.shutdown();
		}
	}

	public static void onServerStarted() {
		if (metrics != null) return;
		try {
			metrics = new Metrics(10168, new File("config"));
		} catch (Throwable t) {
			LOGGER.warn("Failed to start metrics: {}", t.getMessage());
		}
	}
}
