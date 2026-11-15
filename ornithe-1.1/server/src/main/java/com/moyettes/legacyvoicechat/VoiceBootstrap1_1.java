package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.LoaderId;
import com.moyettes.legacyvoicechat.compat.V1_1Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_1;
import net.ornithemc.osl.entrypoints.api.ModInitializer;

public class VoiceBootstrap1_1 implements ModInitializer {

	@Override
	public void init() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.1")
			.minecraft(new V1_1Compat())
			.networking(new VoiceNetworkingCompat1_1())
			.build();

		new Voice().init(ctx);
	}
}
