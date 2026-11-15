package com.moyettes.legacyvoicechat.poseidon;

import com.moyettes.legacyvoicechat.LoaderId;
import com.moyettes.legacyvoicechat.Voice;
import com.moyettes.legacyvoicechat.VoiceContext;
import com.moyettes.legacyvoicechat.packet.VoiceModSupportedPayload;
import com.moyettes.legacyvoicechat.packet.VoiceServerInfoPayload;
import com.moyettes.legacyvoicechat.server.VoiceServer;
import net.minecraft.server.EntityPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * The plugin's work: packet 250 and OSL's handshake for OSL clients, {@code voice|mod_supported} →
 * {@code voice|server_info} (the voice server's port and a one-time token), the voice server's lifecycle, the
 * per-tick player snapshot and joins/leaves. Everything here runs on the main thread except the voice server itself.
 */
public final class PoseidonPlugin implements PayloadRouter.Handler {

	static final String MOD_SUPPORTED = "voice|mod_supported";
	static final String SERVER_INFO = "voice|server_info";

	private final JavaPlugin owner;
	private int tickTask = -1;

	public PoseidonPlugin(JavaPlugin owner) {
		this.owner = owner;
	}

	public boolean enable() {
		try {
			if (!PacketRegistry.install()) {
				Voice.LOGGER.error("Packet 250 is registered by another plugin; OSL clients can't be reached. Disabling.");
				return false;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			Voice.LOGGER.error("Couldn't register packets on this server; disabling", e);
			return false;
		}
		PayloadRouter.set(this);

		new Voice().init(VoiceContext.builder()
			.loader(LoaderId.POSEIDON)
			.minecraftVersion("b1.7.3")
			.minecraft(new PoseidonCompat(owner.getServer()))
			.networking(() -> { }) // the game connection is this class's
			.build());

		PlayerListener listener = new PlayerListener() {
			@Override
			public void onPlayerJoin(PlayerJoinEvent event) {
				onJoin(event.getPlayer());
			}

			@Override
			public void onPlayerQuit(PlayerQuitEvent event) {
				onLeave(event.getPlayer());
			}

			@Override
			public void onPlayerKick(PlayerKickEvent event) {
				if (!event.isCancelled()) onLeave(event.getPlayer());
			}
		};
		PluginManager plugins = owner.getServer().getPluginManager();
		plugins.registerEvent(Event.Type.PLAYER_JOIN, listener, Event.Priority.Monitor, owner);
		plugins.registerEvent(Event.Type.PLAYER_QUIT, listener, Event.Priority.Monitor, owner);
		plugins.registerEvent(Event.Type.PLAYER_KICK, listener, Event.Priority.Monitor, owner);

		tickTask = owner.getServer().getScheduler().scheduleSyncRepeatingTask(owner, Voice::onServerTick, 1L, 1L);
		Voice.onServerTick();
		// Metrics (Voice.onServerStarted) are not started here: whether LVC reports usage is the owner's call (D1).
		if (owner.getServer().getOnlinePlayers().length > 0) {
			Voice.LOGGER.info("Players already online (reload?) get voice when they rejoin");
		}
		return true;
	}

	public void disable() {
		PayloadRouter.set(null);
		if (tickTask != -1) {
			owner.getServer().getScheduler().cancelTask(tickTask);
			tickTask = -1;
		}
		Voice.shutdown();
		PacketRegistry.uninstall();
		OslClients.clear();
	}

	private void onJoin(Player player) {
		EntityPlayer handle = ((CraftPlayer) player).getHandle();
		if (handle.netServerHandler == null || !OslClients.isOsl(handle.netServerHandler.networkManager)) {
			return; // not an OSL client: it wouldn't understand packet 250
		}
		try {
			send(handle, OslHandshake.CHANNEL, OslHandshake.encode(Arrays.asList(OslHandshake.CHANNEL, MOD_SUPPORTED)));
		} catch (IOException e) {
			Voice.LOGGER.warn("Couldn't send the OSL handshake to {}: {}", player.getName(), e.getMessage());
		}
	}

	private void onLeave(Player player) {
		int id = player.getEntityId();
		VoiceServer server = Voice.getVoiceServer();
		if (server != null) server.disconnectPlayer(id);
		Voice.getPlayerAuthTokens().remove(id);
		OslClients.forget(id);
	}

	@Override
	public void handle(EntityPlayer player, String channel, byte[] data) {
		try {
			if (OslHandshake.CHANNEL.equals(channel)) {
				OslClients.setChannels(player.id, OslHandshake.decodeChannels(data));
			} else if (MOD_SUPPORTED.equals(channel)) {
				onModSupported(player, data);
			}
		} catch (IOException | RuntimeException e) {
			Voice.LOGGER.warn("Bad {} payload from {}: {}", channel, player.name, e.toString());
		}
	}

	private void onModSupported(EntityPlayer player, byte[] data) throws IOException {
		VoiceModSupportedPayload payload = new VoiceModSupportedPayload();
		payload.read(new DataInputStream(new ByteArrayInputStream(data)));
		if (!Voice.acceptsClient(player.name, payload.getProtocolVersion())) return;
		VoiceServer server = Voice.getVoiceServer();
		if (server == null || !server.isRunning()) return;

		String token = Voice.generateAuthToken(player.name);
		Voice.getPlayerAuthTokens().put(player.id, token);
		VoiceServerInfoPayload info = new VoiceServerInfoPayload(server.getPort(), token, server.getProtocolVersion(),
			server.getConfig().getServerHost());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		info.write(new DataOutputStream(bytes));
		send(player, SERVER_INFO, bytes.toByteArray());
		Voice.LOGGER.info("{} has LegacyVoiceChat {}", player.name, payload.getProtocolVersion());
	}

	private static void send(EntityPlayer player, String channel, byte[] data) {
		if (player.netServerHandler != null) {
			player.netServerHandler.sendPacket(new CustomPayloadPacket(channel, data));
		}
	}
}
