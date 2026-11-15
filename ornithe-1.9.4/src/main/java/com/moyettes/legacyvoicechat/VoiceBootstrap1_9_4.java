package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.V1_9_4Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_9_4;
import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

public class VoiceBootstrap1_9_4 implements ServerModInitializer {

	@Override
	public void initServer() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.9.4")
			.minecraft(new V1_9_4Compat())
			.networking(new VoiceNetworkingCompat1_9_4())
			.build();

		new Voice().init(ctx);
	}
}
