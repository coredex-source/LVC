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

public class VoiceNetworkingCompatV1_14_4 implements VoiceNetworkingCompat {



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
				if (!Voice.acceptsClient(player.getName().getString(), supported.getProtocolVersion())) {
					return;
				}
				VoiceModSupportedPayload payload = message.get();
				String playerName = player.getName().getString();
				int networkId = player.getNetworkId();

				Voice.LOGGER.info("Player " + playerName + " supports voice mod with protocol " + payload.getProtocolVersion());

				String authToken = Voice.generateAuthToken(playerName);
				Voice.getPlayerAuthTokens().put(networkId, authToken);

				VoiceServerInfoPayload serverInfo = new VoiceServerInfoPayload(
					Voice.getVoiceServer().getPort(),
					authToken,
					Voice.getVoiceServer().getProtocolVersion(),
					Voice.getVoiceServer().getConfig().getServerHost()
				);

				ServerPlayNetworking.send(player, OslPayload.SERVER_INFO, new OslPayload<>(serverInfo));
				Voice.LOGGER.info("Sent voice server info to " + playerName);
			}
		);

		ServerConnectionEvents.DISCONNECT.register(context -> {
			ServerPlayerEntity p = context.player();
			if (Voice.getVoiceServer() != null) {
				Voice.getVoiceServer().disconnectPlayer(p.getNetworkId());
			}
			Voice.getPlayerAuthTokens().remove(p.getNetworkId());
		});
	}
}
