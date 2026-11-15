package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.V1_8_9Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_8_9;
import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

public class VoiceBootstrap1_8_9 implements ServerModInitializer {

	@Override
	public void initServer() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.8.9")
			.minecraft(new V1_8_9Compat())
			.networking(new VoiceNetworkingCompat1_8_9())
			.build();

		new Voice().init(ctx);
	}
}
