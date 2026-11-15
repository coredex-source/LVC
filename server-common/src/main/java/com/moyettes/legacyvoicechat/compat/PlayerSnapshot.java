package com.moyettes.legacyvoicechat.compat;

/** A player as the game thread saw them at the last tick: safe to read from the voice server's thread. */
public final class PlayerSnapshot implements VoicePlayer {

	private final int networkId;
	private final String name;
	private final double x, y, z;
	private final Object world;

	public PlayerSnapshot(VoicePlayer player) {
		this.networkId = player.getNetworkId();
		this.name = player.getName();
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
		this.world = player.getWorld();
	}

	@Override public int getNetworkId() { return networkId; }
	@Override public String getUniqueId() { return Integer.toString(networkId); }
	@Override public String getName() { return name; }
	@Override public double getX() { return x; }
	@Override public double getY() { return y; }
	@Override public double getZ() { return z; }
	@Override public Object getWorld() { return world; }
}
