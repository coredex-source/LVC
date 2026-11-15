package com.moyettes.legacyvoicechat.client;

import com.moyettes.legacyvoicechat.LoaderId;
import com.moyettes.legacyvoicechat.client.VoiceClient;
import com.moyettes.legacyvoicechat.client.VoiceClientBoot;
import com.moyettes.legacyvoicechat.client.VoiceClientContext;
import com.moyettes.legacyvoicechat.client.VoiceSession;
import com.moyettes.legacyvoicechat.client.bridge.GuiBridgeImpl;
import com.moyettes.legacyvoicechat.client.bridge.MinecraftClientAccessorImpl;
import com.moyettes.legacyvoicechat.client.bridge.RenderApiImpl;
import com.moyettes.legacyvoicechat.client.gui.screen.GroupMembersScreen;
import com.moyettes.legacyvoicechat.client.gui.screen.GroupsScreen;
import com.moyettes.legacyvoicechat.client.platform.IRenderApi;
import com.moyettes.legacyvoicechat.events.ClientEvents;
import com.moyettes.legacyvoicechat.events.KeyEvents;
import com.moyettes.legacyvoicechat.events.RenderEvents;
import com.moyettes.legacyvoicechat.mixin.ClientNetworkHandlerMixin;
import com.moyettes.legacyvoicechat.mixin.ConnectionInterfaceMixin;
import com.moyettes.legacyvoicechat.network.OslPayload;
import com.moyettes.legacyvoicechat.packet.VoiceModSupportedPayload;
import com.moyettes.legacyvoicechat.packet.VoiceServerInfoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.Connection;
import net.ornithemc.osl.entrypoints.api.client.ClientModInitializer;
import net.ornithemc.osl.networking.api.client.ClientConnectionEvents;
import net.ornithemc.osl.networking.api.client.ClientPlayNetworking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class Voice implements ClientModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("LegacyVoiceChat");



	private static VoiceClient voiceClient;
	private static final String PROTOCOL_VERSION = com.moyettes.legacyvoicechat.Protocol.VERSION;
	private static final boolean MIC_ENABLED = true;
	private static volatile boolean hideIcons = false;
	public static KeyEvents keyEvents;

	@Override
	public void initClient() {
		LOGGER.info("Initializing voice mod!");

		IRenderApi renderApi = new RenderApiImpl();
		VoiceClientContext ctx = VoiceClientContext.builder()
			.loader(LoaderId.ORNITHE)
			.minecraftVersion("1.7.2")
			.render(renderApi)
			.client(new MinecraftClientAccessorImpl())
			.gui(new GuiBridgeImpl(renderApi))
			.build();
		if (VoiceClientBoot.getContext() == null) {
			VoiceClientBoot.start(ctx);
		}

		registerNetworkListeners();
		new ClientEvents();
		new RenderEvents();
		keyEvents = new KeyEvents();
	}

	private void registerNetworkListeners() {
		ClientPlayNetworking.registerListener(OslPayload.SERVER_INFO, () -> new OslPayload<>(new VoiceServerInfoPayload()),
			(context, message) -> {
				// From 1.8 on payloads arrive on the network thread: OSL runs this again on the main thread.
				context.ensureOnMainThread();
				VoiceServerInfoPayload payload = message.get();
				if (!VoiceClientBoot.acceptsServer(payload.getProtocolVersion())) {
					return;
				}
				Minecraft minecraft = context.minecraft();
				LOGGER.info("Received voice server info - Port: " + payload.getVoiceServerPort() +
					", Protocol: " + payload.getProtocolVersion());

				connectToVoiceServer(payload, minecraft);
			});

		ClientConnectionEvents.PLAY_READY.register(context -> {
			LOGGER.info("Connected to server, sending voice mod support");
			ClientPlayNetworking.send(OslPayload.MOD_SUPPORTED, new OslPayload<>(new VoiceModSupportedPayload(PROTOCOL_VERSION)));
		});

		ClientConnectionEvents.DISCONNECT.register(context -> forceDisconnect());
	}

	private void connectToVoiceServer(VoiceServerInfoPayload serverInfo, Minecraft minecraft) {
		try {
			Integer playerNetworkId = getPlayerNetworkId(minecraft);
			if (playerNetworkId == null) {
				LOGGER.warn("Voice server info arrived before the player exists; not connecting");
				return;
			}
			// A second server_info used to replace the client without closing it (sockets, threads, double audio).
			forceDisconnect();
			voiceClient = new VoiceClient(playerNetworkId, MIC_ENABLED);
			VoiceSession.setActive(voiceClient);

			voiceClient.setHandshakeCallback(ack -> {
				if (!ack.isSuccess()) {
					LOGGER.warn("Failed to connect to voice server: " + ack.getMessage());
				} else {
					// VoiceClient keeps the listener position up to date itself.
				}
			});

			String serverHost = serverInfo.getServerHost();

			if (serverHost == null || serverHost.isEmpty() || "0.0.0.0".equals(serverHost) || "::".equals(serverHost)) {
				try {
					InetAddress resolvedAddress = resolveServerHostFromConnection(minecraft);
					if (resolvedAddress != null) {
						serverHost = resolvedAddress.getHostAddress();
					} else {
						serverHost = "127.0.0.1";
					}
				} catch (Exception e) {
					LOGGER.warn("Could not resolve 0.0.0.0, using localhost: " + e.getMessage());
					serverHost = "127.0.0.1";
				}
			}

			voiceClient.setGroupListCallback(groupList -> {
				GroupsScreen.updateCurrentGroupList(groupList.getGroups());
			});

			voiceClient.setGroupMemberCallback(memberNames -> {
				GroupMembersScreen.updateCurrentMemberList(memberNames);
			});

			voiceClient.connect(serverHost, serverInfo.getVoiceServerPort(), serverInfo.getAuthToken());

		} catch (IOException e) {
			LOGGER.error("Failed to connect to voice server: " + e.getMessage());
		}
	}

	private InetAddress resolveServerHostFromConnection(Minecraft minecraft) {
		ClientPlayNetworkHandler networkHandler = minecraft.getNetworkHandler();
		if (networkHandler == null) throw new IllegalStateException("No network handler");

		ClientNetworkHandlerMixin accessor = (ClientNetworkHandlerMixin) networkHandler;
		Connection connection = accessor.getConnection();
		if (connection == null) throw new IllegalStateException("No connection");

		ConnectionInterfaceMixin connectionAccessor = (ConnectionInterfaceMixin) connection;
		SocketAddress remoteAddress = connectionAccessor.getAddress();
		if (!(remoteAddress instanceof InetSocketAddress)) throw new IllegalStateException("Not an InetSocketAddress");

		return ((InetSocketAddress) remoteAddress).getAddress();
	}

	private Integer getPlayerNetworkId(Minecraft minecraft) {
		try {
			if (minecraft != null && minecraft.player != null) {
				return minecraft.player.getNetworkId();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to get player network ID: " + e.getMessage());
		}
		return null;
	}

	public static VoiceClient getVoiceClient() {
		return voiceClient;
	}

	public static void createGroup(String groupName, String password) {
		if (voiceClient != null) {
			voiceClient.createGroup(groupName, password);
		} else {
			LOGGER.warn("Cannot create group: voice client not connected");
		}
	}

	public static void joinGroup(String groupName, String password) {
		if (voiceClient != null) {
			voiceClient.joinGroup(groupName, password);
		} else {
			LOGGER.warn("Cannot join group: voice client not connected");
		}
	}

	public static void leaveGroup() {
		if (voiceClient != null) {
			voiceClient.leaveGroup();
		} else {
			LOGGER.warn("Cannot leave group: voice client not connected");
		}
	}

	public static void requestGroupList() {
		if (voiceClient != null) {
			voiceClient.requestGroupList();
		} else {
			LOGGER.warn("Cannot request group list: voice client not connected");
		}
	}

	public static boolean isInGroup() {
		return voiceClient != null && voiceClient.isInGroup();
	}

	public static String getCurrentGroupName() {
		return voiceClient != null ? voiceClient.getCurrentGroupName() : null;
	}

	public static void forceDisconnect() {
		if (voiceClient != null) {
			voiceClient.disconnect();
			voiceClient = null;
			VoiceSession.setActive(null);
		}
	}

	public static void toggleIcons() {
		hideIcons = !hideIcons;
	}

	public static boolean isIconsHidden() {
		return hideIcons;
	}

}
