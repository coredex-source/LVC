package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.V1_10_2Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_10_2;
import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

public class VoiceBootstrap1_10_2 implements ServerModInitializer {

	@Override
	public void initServer() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.10.2")
			.minecraft(new V1_10_2Compat())
			.networking(new VoiceNetworkingCompat1_10_2())
			.build();

		new Voice().init(ctx);
	}
}
