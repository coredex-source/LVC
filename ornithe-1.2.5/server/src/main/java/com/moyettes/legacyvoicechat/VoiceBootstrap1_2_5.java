package com.moyettes.legacyvoicechat;

import com.moyettes.legacyvoicechat.LoaderId;
import com.moyettes.legacyvoicechat.compat.V1_2_5Compat;
import com.moyettes.legacyvoicechat.compat.networking.VoiceNetworkingCompat1_2_5;
import net.ornithemc.osl.entrypoints.api.ModInitializer;

public class VoiceBootstrap1_2_5 implements ModInitializer {

	@Override
	public void init() {
		VoiceContext ctx = VoiceContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.2.5")
			.minecraft(new V1_2_5Compat())
			.networking(new VoiceNetworkingCompat1_2_5())
			.build();

		new Voice().init(ctx);
	}
}
