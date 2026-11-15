package com.moyettes.legacyvoicechat.compat.networking;

import com.moyettes.legacyvoicechat.Voice;
import com.moyettes.legacyvoicechat.network.OslPayload;
import com.moyettes.legacyvoicechat.packet.VoiceModSupportedPayload;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import com.moyettes.legacyvoicechat.packet.VoiceServerInfoPayload;
import net.ornithemc.osl.lifecycle.api.server.MinecraftServerEvents;
import net.ornithemc.osl.networking.api.server.ServerConnectionEvents;
import net.ornithemc.osl.networking.api.server.ServerPlayNetworking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class VoiceNetworkingCompat1_11_2 implements VoiceNetworkingCompat {



	@Override
	public void registerNetworkListeners() {
		// Players and positions for the voice server, captured on the game thread (it used to read the game's
		// player list from its own threads); and stop it with the server.
		MinecraftServerEvents.TICK_END.register(server -> Voice.onServerTick());
		MinecraftServerEvents.STOP.register(server -> Voice.shutdown());

		ServerPlayNetworking.registerListener(OslPayload.MOD_SUPPORTED, () -> new OslPayload<>(new VoiceModSupportedPayload()),
			(context, message) -> {
				// From 1.8 on payloads arrive on the network thread: OSL runs this again on the main thread.
				context.ensureOnMainThread();
				ServerPlayerEntity player = context.player();
				VoiceModSupportedPayload supported = message.get();
				if (!Voice.acceptsClient(player.getName(), supported.getProtocolVersion())) {
					return;
				}
				String name = player.getName();
				int id = player.getNetworkId();

				String token = Voice.generateAuthToken(name);
				Voice.getPlayerAuthTokens().put(id, token);

				VoiceServerInfoPayload info = new VoiceServerInfoPayload(
					Voice.getVoiceServer().getPort(),
					token,
					Voice.getVoiceServer().getProtocolVersion(),
					Voice.getVoiceServer().getConfig().getServerHost()
				);

				ServerPlayNetworking.send(player, OslPayload.SERVER_INFO, new OslPayload<>(info));
			});

		ServerConnectionEvents.DISCONNECT.register(context -> {
			ServerPlayerEntity player = context.player();
			Voice.getVoiceServer().disconnectPlayer(player.getNetworkId());
			Voice.getPlayerAuthTokens().remove(player.getNetworkId());
		});
	}
}
