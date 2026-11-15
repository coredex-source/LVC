package com.moyettes.legacyvoicechat.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class VoiceClientBoot {

	public static final Logger LOGGER = LogManager.getLogger("LegacyVoiceChat");

	private static VoiceClientContext context;

	private VoiceClientBoot() {}

	public static void start(VoiceClientContext ctx) {
		if (context != null) {
			throw new IllegalStateException("VoiceClientBoot has already been started");
		}
		context = ctx;
		LOGGER.info("LegacyVoiceChat client booting on {} ({})", ctx.loader, ctx.minecraftVersion);
	}

	/** Whether this client can use a server announcing {@code protocolVersion} (same major version); logs when not. */
	public static boolean acceptsServer(String protocolVersion) {
		if (com.moyettes.legacyvoicechat.Protocol.isCompatible(protocolVersion)) return true;
		LOGGER.warn("The server has LegacyVoiceChat protocol {}, this client {}: no voice until one side is updated",
			protocolVersion, com.moyettes.legacyvoicechat.Protocol.VERSION);
		return false;
	}

	public static VoiceClientContext getContext() {
		return context;
	}
}
