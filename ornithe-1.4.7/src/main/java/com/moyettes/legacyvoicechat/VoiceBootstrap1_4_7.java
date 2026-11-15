package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.V1_4_7Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_4_7;
import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

public class VoiceBootstrap1_4_7 implements ServerModInitializer {

	@Override
	public void initServer() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.4.7")
			.minecraft(new V1_4_7Compat())
			.networking(new VoiceNetworkingCompat1_4_7())
			.build();

		new Voice().init(ctx);
	}
}
