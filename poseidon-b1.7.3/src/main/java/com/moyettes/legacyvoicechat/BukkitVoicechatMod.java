package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.log.VoiceLog;
import com.moyettes.legacyvoicechat.poseidon.PoseidonPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LegacyVoiceChat for Project Poseidon (Beta 1.7.3 servers): the plugin's main class (plugin.yml); the work is in
 * {@link PoseidonPlugin}.
 */
public class BukkitVoicechatMod extends JavaPlugin {

	private PoseidonPlugin plugin;

	@Override
	public void onEnable() {
		final Logger log = getServer().getLogger();
		VoiceLog.setSink((level, message, error) -> log.log(
			level == VoiceLog.ERROR ? Level.SEVERE : level == VoiceLog.WARN ? Level.WARNING : Level.INFO,
			"[LegacyVoiceChat] " + message, error));
		plugin = new PoseidonPlugin(this);
		if (!plugin.enable()) {
			plugin = null;
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		if (plugin != null) {
			plugin.disable();
			plugin = null;
		}
	}
}
