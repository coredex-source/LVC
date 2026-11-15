package com.moyettes.legacyvoicechat.server;

import com.moyettes.legacyvoicechat.Protocol;
import com.moyettes.legacyvoicechat.Voice;
import com.moyettes.legacyvoicechat.compat.MinecraftCompat;
import com.moyettes.legacyvoicechat.compat.VoicePlayer;
import com.moyettes.legacyvoicechat.config.VoiceServerConfig;
import com.moyettes.legacyvoicechat.udp.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The UDP voice server. One thread receives and handles every packet in arrival order and owns all session and group
 * state; other threads (the game thread: disconnects) hand work to it through {@link #tasks}. Players and positions
 * come from the game thread's per-tick snapshot ({@link Voice#getPlayerSnapshot()}).
 * <p>
 * A player is bound to its UDP address by the handshake (a single-use token handed out over the game connection);
 * every later packet is attributed to the player bound to its sender address, whatever player id it claims.
 */
public class VoiceServer {

	/** Group names and passwords at most this long. */
	private static final int MAX_NAME_LENGTH = 32;
	/** Opus frames are far smaller; anything larger isn't voice. */
	private static final int MAX_AUDIO_BYTES = 1500;
	/** No packet from a session for this long: it is dropped (clients ping every couple of seconds). */
	private static final long SESSION_TIMEOUT_MS = 30_000;
	private static final int RECEIVE_TIMEOUT_MS = 500;

	private DatagramSocket socket;
	private Thread thread;
	private volatile boolean running = false;
	private final int port;
	private final VoiceServerConfig config;

	private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
	private final Map<SocketAddress, Session> sessionsByAddress = new ConcurrentHashMap<>();
	private final Queue tasks = new Queue();

	// Owned by the receive thread.
	private final Map<String, Group> groups = new HashMap<>();
	private final Map<Integer, String> playerToGroup = new HashMap<>();
	private long lastExpiryCheck;

	private static final class Session {
		final int playerId;
		volatile SocketAddress address;
		final String token;
		volatile long lastHeard;
		volatile boolean voiceSupported = true;
		volatile boolean deafened = false;
		/** Voice: 50 frames/s normally; control (groups, presence, pings): a few per second. */
		final RateLimit voice = new RateLimit(100, 75);
		final RateLimit control = new RateLimit(20, 5);

		Session(int playerId, SocketAddress address, String token) {
			this.playerId = playerId;
			this.address = address;
			this.token = token;
			this.lastHeard = System.currentTimeMillis();
		}
	}

	private static final class Queue extends ConcurrentLinkedQueue<Runnable> {
	}

	/** Token bucket: {@code burst} packets at once, refilled at {@code perSecond}. Receive thread only. */
	private static final class RateLimit {
		private final double burst, perSecond;
		private double tokens;
		private long last = System.nanoTime();

		RateLimit(double burst, double perSecond) {
			this.burst = burst;
			this.perSecond = perSecond;
			this.tokens = burst;
		}

		boolean allow() {
			long now = System.nanoTime();
			tokens = Math.min(burst, tokens + (now - last) / 1e9 * perSecond);
			last = now;
			if (tokens < 1) return false;
			tokens -= 1;
			return true;
		}
	}

	public VoiceServer() {
		this.config = VoiceServerConfig.load();
		this.port = config.getPort();
	}

	public void start() throws IOException {
		if (running) return;

		socket = new DatagramSocket(port);
		try {
			socket.setReceiveBufferSize(262144);
			socket.setSendBufferSize(262144);
			socket.setTrafficClass(0x04);
		} catch (Exception e) {
			Voice.LOGGER.warn("Failed to set socket buffer sizes: {}", e.getMessage());
		}
		socket.setSoTimeout(RECEIVE_TIMEOUT_MS);

		running = true;
		thread = new Thread(this::run, "LegacyVoiceChat voice server");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		running = false;
		if (socket != null && !socket.isClosed()) socket.close();
		if (thread != null) {
			try {
				thread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		sessions.clear();
		sessionsByAddress.clear();
	}

	private void run() {
		byte[] buffer = new byte[2048];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while (running && !socket.isClosed()) {
			try {
				packet.setLength(buffer.length);
				socket.receive(packet);
				handlePacket(packet);
			} catch (SocketTimeoutException timeout) {
				// time for the tasks and the expiry check below
			} catch (IOException e) {
				if (running && !quiet(e)) {
					Voice.LOGGER.warn("Error receiving packet: {}", e.getMessage());
				}
			} catch (RuntimeException e) {
				Voice.LOGGER.error("Unexpected error handling a voice packet", e);
			}
			runTasks();
			expireSessions();
		}
	}

	private void runTasks() {
		Runnable task;
		while ((task = tasks.poll()) != null) {
			try {
				task.run();
			} catch (RuntimeException e) {
				Voice.LOGGER.error("Voice server task failed", e);
			}
		}
	}

	private void expireSessions() {
		long now = System.currentTimeMillis();
		if (now - lastExpiryCheck < 1000) return;
		lastExpiryCheck = now;
		for (Session session : new ArrayList<>(sessions.values())) {
			if (now - session.lastHeard > SESSION_TIMEOUT_MS) {
				removeSession(session.playerId);
			}
		}
	}

	private void handlePacket(DatagramPacket packet) {
		int length = packet.getLength();
		if (length <= 0) return;
		byte[] data = new byte[length];
		System.arraycopy(packet.getData(), packet.getOffset(), data, 0, length);
		SocketAddress sender = packet.getSocketAddress();

		UdpPacket udpPacket;
		try {
			udpPacket = UdpPacket.fromBytes(data);
		} catch (IOException | RuntimeException e) {
			return; // garbage or a foreign protocol: not worth a log line per packet
		}

		if (udpPacket.getType() == UdpPacket.PacketType.VOICE_HANDSHAKE) {
			handleHandshake((VoiceHandshakePacket) udpPacket, sender);
			return;
		}

		// Everything else needs a session, found by the sender's address (never by the id a packet claims).
		Session session = sessionsByAddress.get(sender);
		if (session == null) return;
		session.lastHeard = System.currentTimeMillis();
		boolean audio = udpPacket.getType() == UdpPacket.PacketType.VOICE_DATA || udpPacket.getType() == UdpPacket.PacketType.GROUP_DATA;
		if (!(audio ? session.voice : session.control).allow()) return; // over its rate: dropped

		switch (udpPacket.getType()) {
			case VOICE_DATA:
				byte[] audioData = ((VoiceDataPacket) udpPacket).getAudioData();
				if (audioData == null || audioData.length > MAX_AUDIO_BYTES) return;
				relayAudioData(session.playerId, audioData, ((VoiceDataPacket) udpPacket).getSequenceNumber());
				break;
			case GROUP_CREATE:
				handleGroupCreate((GroupCreatePacket) udpPacket, session);
				break;
			case GROUP_JOIN:
				handleGroupJoin((GroupJoinPacket) udpPacket, session);
				break;
			case GROUP_LEAVE:
				leaveGroup(session.playerId);
				break;
			case GROUP_LIST:
				handleGroupList(sender);
				break;
			case GROUP_DATA:
				handleGroupData((GroupDataPacket) udpPacket, session);
				break;
			case PING:
				// Only the server answers pings: the client used to answer the answer, so one ping started an endless
				// ping-pong at network speed.
				sendPacket(new PingPacket(), sender);
				break;
			case PRESENCE_UPDATE:
				PresenceUpdatePacket presence = (PresenceUpdatePacket) udpPacket;
				session.voiceSupported = presence.isVoiceSupported();
				session.deafened = presence.isDeafened();
				broadcastPresenceUpdate(session.playerId, session.voiceSupported, session.deafened);
				break;
			default:
				break;
		}
	}

	private void handleHandshake(VoiceHandshakePacket handshake, SocketAddress sender) {
		Integer claimedId = handshake.getPlayerNetworkId();
		String token = handshake.getAuthToken();
		if (claimedId == null || token == null) return;

		Session existing = sessions.get(claimedId);
		String pending = Voice.getPlayerAuthToken(claimedId);
		boolean fresh = pending != null && pending.equals(token);
		// A retried handshake (the ack was lost) or a new address (NAT rebinding) carries the token already used.
		boolean again = existing != null && existing.token.equals(token);

		if (!fresh && !again) {
			sendPacket(new VoiceHandshakeAckPacket(false, "Invalid auth token"), sender);
			return;
		}
		if (fresh) {
			Voice.getPlayerAuthTokens().remove(claimedId, token); // single use
		}

		Session session;
		if (again) {
			session = existing;
			if (!sender.equals(session.address)) {
				sessionsByAddress.remove(session.address, session);
				session.address = sender;
			}
			session.lastHeard = System.currentTimeMillis();
		} else {
			if (existing != null) {
				removeSession(existing.playerId);
			}
			session = new Session(claimedId, sender, token);
			sessions.put(claimedId, session);
		}
		sessionsByAddress.put(sender, session);

		sendPacket(new VoiceHandshakeAckPacket(true, "Connection established"), sender);
		if (!again) {
			sendPresenceBulkToPlayer(sender);
			broadcastPresenceUpdate(claimedId, true, false);
		}
	}

	private void sendPresenceBulkToPlayer(SocketAddress address) {
		Map<Integer, PresenceBulkPacket.PlayerState> states = new HashMap<>();
		for (Session s : sessions.values()) {
			states.put(s.playerId, new PresenceBulkPacket.PlayerState(s.voiceSupported, s.deafened));
		}
		if (!states.isEmpty()) {
			sendPacket(new PresenceBulkPacket(states), address);
		}
	}

	private void broadcastPresenceUpdate(int playerNetworkId, boolean voiceSupported, boolean deafened) {
		PresenceUpdatePacket packet = new PresenceUpdatePacket(playerNetworkId, voiceSupported, deafened);
		for (Session s : sessions.values()) {
			sendPacket(packet, s.address);
		}
	}

	private void relayAudioData(int fromPlayerId, byte[] audioData, long sequenceNumber) {
		String groupName = playerToGroup.get(fromPlayerId);
		if (groupName != null) {
			relayGroupAudio(fromPlayerId, audioData, sequenceNumber, groupName);
		} else {
			relayProximityAudio(fromPlayerId, audioData, sequenceNumber);
		}
	}

	private void relayGroupAudio(int fromPlayerId, byte[] audioData, long sequenceNumber, String groupName) {
		Group group = groups.get(groupName);
		if (group == null || !group.members.contains(fromPlayerId)) return;
		VoiceDataRelayPacket relay = new VoiceDataRelayPacket(fromPlayerId, audioData, sequenceNumber, 0.0, 1.0f, 0.0, 0.0, 0.0);
		for (Integer memberId : group.members) {
			if (memberId != fromPlayerId) {
				Session member = sessions.get(memberId);
				if (member != null) sendPacket(relay, member.address);
			}
		}
	}

	private void relayProximityAudio(int fromPlayerId, byte[] audioData, long sequenceNumber) {
		Map<Integer, VoicePlayer> players = players();
		VoicePlayer sender = players.get(fromPlayerId);
		if (sender == null) return;

		double maxDistance = config.getAudio().getMaxDistance();
		double maxDistanceSquared = maxDistance * maxDistance * 1.25;
		if (maxDistanceSquared <= 0) return;

		for (Session recipient : sessions.values()) {
			if (recipient.playerId == fromPlayerId || playerToGroup.containsKey(recipient.playerId)) continue;
			VoicePlayer listener = players.get(recipient.playerId);
			if (listener == null) continue;
			Object world = sender.getWorld();
			if (world == null || !world.equals(listener.getWorld())) continue;

			double dx = sender.getX() - listener.getX();
			double dy = sender.getY() - listener.getY();
			double dz = sender.getZ() - listener.getZ();
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			if (distanceSquared > maxDistanceSquared) continue;

			double distance = Math.sqrt(distanceSquared);
			sendPacket(new VoiceDataRelayPacket(fromPlayerId, audioData, sequenceNumber, distance,
				calculateVolumeAttenuation(distance, maxDistance), sender.getX(), sender.getY(), sender.getZ()), recipient.address);
		}
	}

	/** The game thread's snapshot; without one (a platform that doesn't tick us), the game's own list. */
	private Map<Integer, VoicePlayer> players() {
		Map<Integer, VoicePlayer> snapshot = Voice.getPlayerSnapshot();
		if (snapshot != null) return snapshot;
		MinecraftCompat compat = Voice.getContext() != null ? Voice.getContext().mc : null;
		Map<Integer, VoicePlayer> out = new HashMap<>();
		if (compat != null) {
			try {
				for (VoicePlayer p : compat.getAllConnectedPlayers()) out.put(p.getNetworkId(), p);
			} catch (RuntimeException e) {
				// the game's list changed under us; try again next packet
			}
		}
		return out;
	}

	private void sendPacket(UdpPacket packet, SocketAddress address) {
		if (address == null) return;
		try {
			byte[] data = packet.toBytes();
			socket.send(new DatagramPacket(data, data.length, address));
		} catch (IOException e) {
			if (!quiet(e)) {
				Voice.LOGGER.warn("Error sending packet to {}: {}", address, e.getMessage());
			}
		}
	}

	private static boolean quiet(IOException e) {
		String m = e.getMessage();
		return m != null && (m.contains("Connection reset") || m.contains("Connection refused") || m.contains("Network is unreachable")
			|| m.contains("Socket closed") || m.contains("Broken pipe"));
	}

	public int getPort() {
		return port;
	}

	public String getProtocolVersion() {
		return Protocol.VERSION;
	}

	public boolean isRunning() {
		return running;
	}

	/** The player left the game: callable from any thread (done on the voice thread). */
	public void disconnectPlayer(Integer playerNetworkId) {
		if (playerNetworkId == null) return;
		tasks.add(() -> removeSession(playerNetworkId));
	}

	private void removeSession(int playerId) {
		Session session = sessions.remove(playerId);
		if (session != null) {
			sessionsByAddress.remove(session.address, session);
			broadcastPresenceUpdate(playerId, false, false);
		}
		leaveGroup(playerId);
	}

	public Map<SocketAddress, Integer> getConnectedPlayers() {
		Map<SocketAddress, Integer> out = new HashMap<>();
		for (Session s : sessions.values()) out.put(s.address, s.playerId);
		return out;
	}

	public VoiceServerConfig getConfig() {
		return config;
	}

	private float calculateVolumeAttenuation(double distance, double maxDistance) {
		if (distance <= 0) return 1.0f;
		double fadeDistance = maxDistance / 8;
		if (distance <= fadeDistance) return 1.0f;
		float rolloffFactor = 0.95f;
		float attenuation = (float) (fadeDistance / (fadeDistance + rolloffFactor * (distance - fadeDistance)));
		return Math.max(0.0f, Math.min(1.0f, attenuation));
	}

	private void handleGroupCreate(GroupCreatePacket packet, Session session) {
		String groupName = packet.getGroupName();
		if (groupName == null || groupName.trim().isEmpty()) {
			reject(session, "", "A group needs a name");
			return;
		}
		groupName = groupName.trim();
		if (groupName.length() > MAX_NAME_LENGTH || (packet.getPassword() != null && packet.getPassword().length() > MAX_NAME_LENGTH)) {
			reject(session, groupName, "Name and password: at most " + MAX_NAME_LENGTH + " characters");
			return;
		}
		if (groups.containsKey(groupName)) {
			reject(session, groupName, "A group with that name exists");
			return;
		}

		if (playerToGroup.containsKey(session.playerId)) {
			leaveGroup(session.playerId);
		}
		Group group = new Group(groupName, packet.getPassword(), session.playerId);
		groups.put(groupName, group);
		playerToGroup.put(session.playerId, groupName);

		sendGroupMemberUpdate(GroupMemberUpdatePacket.UpdateType.GROUP_CREATED, groupName, session.playerId,
			getPlayerName(session.playerId), new ArrayList<>(group.members));
	}

	private void handleGroupJoin(GroupJoinPacket packet, Session session) {
		String groupName = packet.getGroupName();
		if (groupName == null || groupName.trim().isEmpty()) return;
		groupName = groupName.trim();

		Group group = groups.get(groupName);
		if (group == null) {
			reject(session, groupName, "That group doesn't exist any more");
			return;
		}
		// Check the password before leaving the current group (a wrong one used to drop you from yours first).
		if (group.hasPassword() && !group.password.equals(packet.getPassword())) {
			reject(session, groupName, "Wrong password");
			return;
		}

		if (playerToGroup.containsKey(session.playerId)) {
			leaveGroup(session.playerId);
		}
		group.members.add(session.playerId);
		playerToGroup.put(session.playerId, groupName);

		sendGroupMemberUpdate(GroupMemberUpdatePacket.UpdateType.MEMBER_JOINED, groupName, session.playerId,
			getPlayerName(session.playerId), new ArrayList<>(group.members));
	}

	private void handleGroupList(SocketAddress sender) {
		List<GroupListPacket.GroupInfo> list = new ArrayList<>();
		for (Group group : groups.values()) {
			list.add(new GroupListPacket.GroupInfo(group.name, group.hasPassword(), group.members.size()));
		}
		sendPacket(new GroupListPacket(list), sender);
	}

	private void handleGroupData(GroupDataPacket packet, Session session) {
		String groupName = packet.getGroupName();
		if (groupName == null || !groupName.equals(playerToGroup.get(session.playerId))) return;
		relayGroupAudio(session.playerId, packet.getAudioData(), packet.getSequenceNumber(), groupName);
	}

	private void leaveGroup(int playerId) {
		String groupName = playerToGroup.remove(playerId);
		if (groupName == null) return;
		Group group = groups.get(groupName);
		if (group == null) return;

		group.members.remove(playerId);
		sendGroupMemberUpdate(GroupMemberUpdatePacket.UpdateType.MEMBER_LEFT, groupName, playerId,
			getPlayerName(playerId), new ArrayList<>(group.members));

		if (group.members.isEmpty()) {
			groups.remove(groupName);
		}
	}

	private void sendGroupMemberUpdate(GroupMemberUpdatePacket.UpdateType updateType, String groupName,
	                                   Integer playerId, String playerName, List<Integer> groupMembers) {
		List<String> memberNames = new ArrayList<>();
		for (Integer memberId : groupMembers) {
			memberNames.add(getPlayerName(memberId));
		}
		GroupMemberUpdatePacket update = new GroupMemberUpdatePacket(updateType, groupName, playerId, playerName, memberNames);
		// Only the group's members and the player who joined or left: member names used to go to everyone.
		Set<Integer> recipients = new HashSet<>(groupMembers);
		if (playerId != null) recipients.add(playerId);
		for (Integer id : recipients) {
			Session s = sessions.get(id);
			if (s != null) sendPacket(update, s.address);
		}
	}

	private void reject(Session session, String groupName, String reason) {
		sendPacket(new GroupMemberUpdatePacket(GroupMemberUpdatePacket.UpdateType.REJECTED, groupName, session.playerId, reason,
			Collections.<String>emptyList()), session.address);
	}

	private String getPlayerName(Integer playerId) {
		VoicePlayer player = playerId != null ? players().get(playerId) : null;
		return player != null ? player.getName() : "Unknown";
	}

	private static final class Group {
		final String name;
		final String password;
		final Set<Integer> members = new HashSet<>();

		Group(String name, String password, int creatorId) {
			this.name = name;
			this.password = password;
			this.members.add(creatorId);
		}

		boolean hasPassword() {
			return password != null && !password.isEmpty();
		}
	}
}
