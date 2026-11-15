package com.moyettes.legacyvoicechat.poseidon;

import com.moyettes.legacyvoicechat.compat.MinecraftCompat;
import com.moyettes.legacyvoicechat.compat.VoicePlayer;
import net.minecraft.server.EntityPlayer;
import org.bukkit.Server;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The server's players for the voice server; called on the main thread (the per-tick snapshot). */
final class PoseidonCompat implements MinecraftCompat {

	private final Server server;

	PoseidonCompat(Server server) {
		this.server = server;
	}

	@Override
	public VoicePlayer getPlayerById(int networkId) {
		for (VoicePlayer p : getAllConnectedPlayers()) {
			if (p.getNetworkId() == networkId) return p;
		}
		return null;
	}

	@Override
	public VoicePlayer getPlayerByName(String name) {
		for (VoicePlayer p : getAllConnectedPlayers()) {
			if (p.getName().equals(name)) return p;
		}
		return null;
	}

	@Override
	public Collection<VoicePlayer> getAllConnectedPlayers() {
		List<VoicePlayer> out = new ArrayList<>();
		for (Player player : server.getOnlinePlayers()) {
			if (player instanceof CraftPlayer) {
				out.add(new PoseidonPlayer(((CraftPlayer) player).getHandle()));
			}
		}
		return out;
	}

	@Override
	public double getDistanceSquared(VoicePlayer a, VoicePlayer b) {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public boolean isOnlineMode() {
		return server.getOnlineMode();
	}

	static final class PoseidonPlayer implements VoicePlayer {
		private final EntityPlayer player;

		PoseidonPlayer(EntityPlayer player) {
			this.player = player;
		}

		@Override public int getNetworkId() { return player.id; }
		@Override public String getUniqueId() { return Integer.toString(player.id); }
		@Override public String getName() { return player.name; }
		@Override public double getX() { return player.locX; }
		@Override public double getY() { return player.locY; }
		@Override public double getZ() { return player.locZ; }
		@Override public Object getWorld() { return player.world; }
	}
}
