package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.compat.V1_3_2Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_3_2;
import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

public class VoiceBootstrap1_3_2 implements ServerModInitializer {

	@Override
	public void initServer() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.3.2")
			.minecraft(new V1_3_2Compat())
			.networking(new VoiceNetworkingCompat1_3_2())
			.build();

		new Voice().init(ctx);
	}
}
