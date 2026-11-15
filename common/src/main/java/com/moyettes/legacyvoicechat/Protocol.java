package com.moyettes.legacyvoicechat;

/**
 * LegacyVoiceChat's protocol version, sent both ways at connection (mod_supported, server_info). Sides talk only when
 * the major versions match. 2.0.0 (2026-11-15): servers bind players to their UDP address, only servers answer pings,
 * and group updates can be REJECTED (with a reason).
 */
public final class Protocol {

	public static final String VERSION = "2.0.0";

	private Protocol() {
	}

	public static boolean isCompatible(String other) {
		return other != null && major(other).equals(major(VERSION));
	}

	private static String major(String version) {
		int dot = version.indexOf('.');
		return dot < 0 ? version.trim() : version.substring(0, dot).trim();
	}
}
