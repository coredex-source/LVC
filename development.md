# LegacyVoiceChat: development plan

Proximity voice chat for old Minecraft: Ornithe (gen2) mods for b1.0 to 1.14.4 and a Bukkit plugin for Project
Poseidon (Beta 1.7.3 servers). Order of work: **bugs first, b1.7.3 first** (the owner's server starts at b1.7.3 and moves
to a new version every week), then robustness on every version, then features (compared with Simple Voice Chat, SVC).

Status (2026-11-15): the Ornithe modules are on gen2 and build; Phase 1 (b1.7.3 bugs) is done: voice works end to end
on Poseidon and on an Ornithe server. Next: Phase 2. Nothing has been released from this tree.

## 0. Ground rules

- **Never commit or push**; the owner does.
- **Bugs before features.** Inside each phase, b1.7.3 (client + Poseidon) before other versions.
- **SVC is a feature reference only.** Its repository has no licence, so none of its code is used.
- LVC is MIT (`LICENSE`). It bundles opus-jni-rust and rnnoise-jni-rust (MIT, plasmoapp) whose natives contain libopus
  and RNNoise (BSD-3-Clause); their notices still have to be shipped (2.9).
- Don't build or run downloaded external code without the owner's OK.
- Every mixin target is checked with `tools/check-mixin-targets.py` (Review): Loom 1.19 no longer validates them when
  compiling, so a wrong target only shows up as a crash in game.

## 1. Done: port to Ornithe gen2 (2026-11-15)

- Removed: Babric (BTA, StationAPI), RawRetroMCP, Risugami's ModLoader, the one-off branch-merge scripts
  (`build-tools/`), stray `.bak` files. `LoaderId` still lists the removed loaders (harmless).
- Build: Gradle 9.8.2 (wrapper), Loom 1.19.4 (`net.fabricmc.fabric-loom-remap`), Ploceus 1.19.1, intermediary gen2,
  Feather gen2 build 2 with raven/sparrow/nests per version (`ornithe-*/gradle.properties`), Fabric Loader 0.20.1,
  OSL 0.24.0, Shadow 9.6.1. One shared script, `gradle/ornithe.gradle`; javac from JDK 21, Java 8 bytecode.
  Configure-on-demand: building one module sets up only its game version.
- OSL gen2 networking: LVC's payloads (`common/packet`, Data streams) travel byte for byte through `OslPayload` in each
  module. Channels stay `voice|mod_supported` / `voice|server_info` up to 1.8.8 and `voice:...` on 1.13+ (as before).
  The client sends "mod supported" on `PLAY_READY`, which in gen2 fires after OSL's channel handshake.
- `ConnectionMixin` fired OSL's `DISCONNECT` event by hand, so every mod's disconnect listener ran twice once OSL fires
  it itself; it now calls LVC's own (idempotent) disconnect.
- Feather gen2 class names per version (e.g. `entity.mob.player` on beta and 1.3–1.5, `entity.living.player` on 1.6+,
  `Tesselator` and `begin()` up to 1.7.10). 1.14.4: own accessor for the key-binding category order (it used OSL keybinds'
  internal accessor, gone in gen2), `world.getPlayers()` instead of an unmapped method.
- `fabric.mod.json`: version from the build, exact game versions (the split server mods accepted any version from b1.0 to
  1.2.5 although each is built for one; the b1.4/b1.8 clients declared raw `b1.4_01`/`b1.8`), OSL dependencies
  `osl-entrypoints >=0.6.0`, `osl-networking >=0.12.0` (it only declared entrypoints, so a wrong OSL failed at runtime).
- Jars no longer bundle log4j, Gson (the game provides them) or the unused `su.plo.voice` Opus/RNNoise libraries.
- Checked: all 21 jars build; mixin targets resolve on every module; b1.7.3 on an Ornithe b1.7.3 server: a client with
  Altrender, OSL 0.24.0, Mod Menu and LegacyFix joins, OSL handshake → `mod_supported` → `server_info` → UDP handshake
  acknowledged. **Not checked:** audio between two clients, any other version in game, Poseidon (stub).

## 2. Phase 1: b1.7.3 bugs (P0)

- [x] **1.1 Poseidon plugin does nothing.** `BukkitVoicechatMod.onEnable()` was empty; packet registration was
  commented out and never called. Needs:
  - packet 250 on Poseidon: `[channel: vanilla writeString, max 255][short length][bytes, max 32 KiB]` (what OSL's
    b1.x `CustomPayloadPacket` sends);
  - OSL gen2's channel handshake, or the client never sends anything (OSL drops sends on channels the server didn't
    announce): the client sends `"\0OrnitheMC"` as its handshake string; the server sends `osl:handshake` with
    `byte protocol = 1`, `int count`, then `count` channel names as VarInt-length UTF-8; the client answers with its
    own list, then fires `PLAY_READY`;
  - `voice|mod_supported` → `voice|server_info`, the voice server's lifecycle (start in `onEnable`, stop in `onDisable`,
    survive `/reload`), Bukkit player/world/position access on the main thread;
  - bundle Gson relocated, and log4j relocated or replaced (Poseidon has neither); compile against the current Poseidon
    (`1.1.12-261108-0541-6943873`, set in `poseidon-b1.7.3/gradle.properties`).
  - ✅ **Confirm:** a client joins a Poseidon 1.1.12 server and two clients hear each other.
  - _Note (2026-11-15):_ `poseidon/`: packet 250 and a `Packet2Handshake` subclass (notes OSL connections) put into
    Poseidon's packet tables and taken out again on disable (`PacketRegistry`); the OSL handshake on join (only to OSL
    clients); `voice|mod_supported` → token → `voice|server_info`; players snapshotted every tick by a sync task; quit
    and kick end the voice session. Gson relocated; server-common logs through `log.VoiceLog` (java.util.logging on
    Poseidon, log4j on Ornithe). Metrics are not started on Poseidon until D1. Players online during a `/reload` get
    voice when they rejoin. Checked on Poseidon 1.1.12-261108 with two b1.7.3 clients (Altrender, OSL 0.24.0): both
    connect, and a tone fed into one client's microphone plays on the other (its OpenAL output recorded to a file).
- [x] **1.2 Voice server threading.** Every UDP packet was handled on its own pool task (reordered audio), with a plain
  `HashMap` of connections shared by those tasks; the player cache was cleared and refilled every second while being
  read; player lists and positions were read from voice threads. Now one receive thread owns sessions and groups
  (other threads queue work for it), and players come from a snapshot taken on the game thread each tick
  (`Voice.onServerTick()`: OSL `MinecraftServerEvents.TICK_END` on Ornithe, a sync task on Poseidon). Metrics count
  players from the snapshot too.
- [x] **1.3 Spoofing.** Tokens were `token_<name>_<millis>`; voice and presence packets were accepted for whatever
  player id they claimed. Now: 128-bit `SecureRandom` tokens, single use (a retried handshake with the same token is
  accepted again for that player); the handshake binds the sender address to the player and every later packet is
  attributed by its address. Checked on Poseidon: a guessed old-style token gets "Invalid auth token"; 250 Opus voice
  packets claiming a connected player's id from an unbound address reach nobody.
- [x] **1.4 Game sound stops** (author's report: leaving and rejoining stops game audio). `OpenALAudioPlayback` called
  `AL.destroy()` when a speaker's source closed (on every disconnect) and `AL.create()` when needed: the game's own
  context. It now only makes sources and buffers; without OpenAL it falls back to Java Sound. Not re-run in game
  (leave and rejoin); voice playback through the game's OpenAL is checked (1.1).
- [x] **1.5 Connection reliability.** The UDP handshake is re-sent every second until acknowledged (15 tries, then a
  "UDP port open?" warning). The client pings every 2 s on a timed receive loop whatever comes in, and gives up after
  30 s without an answer; the server drops sessions silent for 30 s and moves a session to a new address that brings
  its token (NAT rebinding). Found on the way: both sides answered every ping, so one ping started an endless
  ping-pong at network speed; now only the server answers. Retries and expiry are not exercised by a test.
- [x] **1.6 Client lifecycle.** A second `server_info` disconnects the previous voice client first; the listener
  position updater belongs to the voice client (one thread, stops with it) instead of a new thread per handshake in
  every version module; without a player yet there is no voice connection instead of the fake id `12345`.
- [x] **1.7 Clicks when turning** (author's report). One balance pan (the near ear at full level, 0 dB in the centre)
  with gains ramped across each frame replaces the switch between √0.5 per channel and 1.0; volume up to 200% is
  honoured (samples clamped). Found on the way: a click at every start of speech (the first decoded frame after a
  pause starts mid-waveform); a stream restarting after 100 ms fades in over its first frame. Checked: turning at
  450°/s past a speaker, the largest sample step is the sine's own (691; the old pan law: 4196); recorded voice on
  Poseidon and Ornithe has no step above a 440 Hz sine's own (27 onset clicks in 27 s before).
- [x] **1.8 Keys while typing.** On the LWJGL2 versions mute, menu and hide-icons were already ignored with a screen
  open (`KeyBindingMixin`); push-to-talk wasn't (it transmitted while typing): now it is. On 1.13 and 1.14.4
  push-to-talk was set on when pressed and never off (stuck transmitting), and mute/menu/hide toggled every tick held:
  now held state for push-to-talk, `consumeClick()` for the toggles. Not run in game.
- [x] **1.9 Esc with the voice GUI open crashes** (author's report). Found and fixed: the shared screens compare GLFW
  key codes (Esc 256, Enter 257, Tab 258, Backspace 259) but the LWJGL2 versions passed their own (1, 28, 15, 14), so
  Enter didn't submit, Tab didn't switch fields and Esc closed everything instead of going back; the 13 LWJGL2 bridges
  translate them now. The crash itself doesn't happen on b1.7.3: a dev test mod drove the screens in game on Poseidon
  (Esc with character 27 and 0 on Settings and Groups, typing + Tab + Esc in Create Group, Create Group with Enter,
  Esc in Join Group): all as expected, no exception, with Altrender and on the game's own LWJGL2.
- [x] **1.10** "Illegal call" from the game's OpenAL library (paulscode): not LVC's. Altrender's shutdown hook destroyed
  the OpenAL context while the game's sound threads still ran when the process was killed (as the test runs end); the
  lines are the last of the log, so no gameplay effect. Fixed in Altrender 0.1.0-alpha.5 (the context is closed only
  after the game's own AL.destroy()).
- Also done here: a wrong group password no longer drops you from your current group (2.5, first part).

## 3. Phase 2: robustness, every version (P1)

- [x] 2.1 A large sequence gap made the client decode one silence frame per missing packet (unbounded loop): capped
  at 4; a sequence far behind the last one (a sender's new voice session) resets that speaker instead of being
  dropped forever.
- [x] 2.2 The client locked each speaker to the first packet type seen (direct/relay/group) and unlocked only on an
  "end" packet nothing sends: the lock is gone (the server only relays).
- [x] 2.3 Protocol versions are compared (major version, `common/Protocol`); now 2.0.0 (address binding, server-only
  ping answers, REJECTED group updates; D3). Servers give no token to an incompatible client, clients don't connect to
  an incompatible server; both log why.
- [x] 2.4 Server limits: group names and passwords 32 characters, audio frames 1500 bytes, per-player rates (voice
  burst 100 at 75/s, control burst 20 at 5/s; over: dropped).
- [x] 2.5 Groups: a wrong password no longer drops you from your group; "name taken", "wrong password", "no such
  group" and over-long names come back as a REJECTED update and show under the voice screen for 5 s; member names go
  only to that group's members. Passwords still travel as plain text until encryption (Phase 3).
- [x] 2.6 `server_host` `0.0.0.0` (or `::`, or empty) resolves to the game connection's address, IPv4 or IPv6 (it fell
  back to `127.0.0.1` for anything but IPv4).
- [x] 2.7 Settings are written a second after the last change, from one background writer (it was every slider step).
- [x] 2.8 Noise suppression (RNNoise) can be switched on: "Noise filter" on the settings screen's bottom row,
  `noise_suppression` in the client config (off by default, as SVC). Checked: on, a tone still reaches the listener
  (RNNoise lowers a steady tone, as expected).
- [x] 2.9 Licence notices in the client jars (`META-INF/licenses/legacyvoicechat/`): the two Plasmo wrappers (MIT) and
  everything linked into their natives (libopus and nnnoiseless/RNNoise BSD-3, and every crate in their Cargo.lock at
  the bundled versions, with licence texts; source: the published crates); Gson's Apache-2.0 notice in the Poseidon jar.
- Found on the way, fixed: the activation-threshold slider sent its raw 0–1000 position where the client expects dB
  (−127…0), clamped to 0 dB: once touched, voice activation never triggered (and the screen read dB back as a
  position).
- [x] 2.10 Every version run in game: a server and a client that joins it, checked through voice info and the voice
  connection (no audio devices, so not sound itself). All pass except b1.0 (no server: OSL) and 1.14.4 (see below).
  Bugs found and fixed:
  - Every 1.3+ dedicated server crashed at start: two client-only mixins were in the common list.
  - From 1.8 on, LVC's own `isPressed()` silently replaced vanilla's, so the client crashed: renamed.
  - From 1.8 on, no client got voice. Payload listeners rerun on the main thread (OSL does this from 14w21a) found
    the packet already read: payloads are now read without consuming the buffer.
  - 1.13 crashed at start (already upstream): its mixin config named two classes the module doesn't have. The
    mixin check tool now reports such entries.
  - b1.8 jars never loaded on b1.8: Fabric calls b1.8 `1.0.0-beta.8.0.r`, the jars asked for `1.0.0-beta.8`
    (upstream asked for `b1.8`).
  - 1.1 and 1.2.x: OSL's packets carry no size there, so no OSL channel worked (the handshake included) and the
    connection broke. LVC now fills in the size (workaround, harmless once OSL fixes it).
  - Packet listeners now run on the game thread (52 of them), where OSL calls them again when asked to.

  Still open:
  - b1.0: an Ornithe b1.0 server with OSL 0.24.0 doesn't start (osl-registries' `MinecraftServerMixinNew` finds
    no target). Not LVC's, but no LVC either until OSL fixes it; the b1.0 client is untested for that reason.
  - 1.14.4: voice connects, then the test client crashes drawing the world (a block with no model). No LVC code is
    in the trace; it looks like the test's join on launch, before 1.14 finished loading models. Needs one normal join.
  - 1.13: one of three runs hung right after joining; not seen again.
- [x] 2.11 Version coverage (D2: the last release of each minor version, not b1.8.1). New modules: 1.1 and 1.2.5
  (separate client and server jars, like b1.x and 1.0.0), 1.3.2, 1.4.7, 1.5.2, 1.6.4, 1.8.9, 1.9.4, 1.10.2, 1.11.2,
  1.12.2. With the existing ones that makes 26 Ornithe modules and 34 jars with Poseidon's. Each jar loads only on its
  own version (Fabric's version check). Fixes on the way: 1.4.7 needed the 1.3.1 texture code, and 1.9–1.12.2 the
  name-tag hook without the partial-tick argument.

## 4. Phase 3: features, compared with SVC (P2)

SVC's side from its Modrinth page and wiki (client and server config), 2026-11-15.

| Feature | SVC | LVC today | Plan |
|---|---|---|---|
| Proximity voice, distance setting | yes (48) | yes (`max_distance` 48) | — |
| Whisper (shorter range) | yes (24) | no | add |
| Push-to-talk, voice activation | yes | yes | — |
| Mic release delay (voice 500 ms, PTT 100 ms) | yes | no: cut at silence or key release, ends of words lost | add |
| Mic gain | yes | 0–200% | — |
| Noise suppression (RNNoise) | option | option, off by default (2.8) | — |
| Per-player volume | yes | yes | — |
| 3D audio (normal / reduced / off) | yes | own stereo panning | modes, smoother panning (1.7) |
| Device choice, mic test | yes | yes | — |
| Groups with password | yes | yes | — |
| Group HUD (talking outline), invites, group icon by names | yes | member list screen only | add |
| Group types (whether nearby non-members hear) | yes | members only | add |
| Icons: talking, whispering, muted, disabled, not installed | yes, positions/scale configurable | talking, muted, not installed; hide | add disabled, config |
| Disable voice chat (no sound, no mic) | yes | deafen + mute | add |
| Recording (one track per speaker) | yes | no | later |
| Encryption (AES) | yes | no | after 1.3 |
| Server config: bind address, voice host, keep-alive, MTU, codec, groups on/off, force voice chat, broadcast range, rate limit | yes | port, host, distance | add as needed |
| Opus modes (VOIP, AUDIO, low delay) | yes | VOIP only | config |
| Jitter buffer tuning | yes | fixed | config |
| Singleplayer / LAN | yes | no (1.3+ integrated server) | later |
| Developer API and addons | yes | no | later |
| Platforms | Fabric, Forge, NeoForge, Quilt; Bukkit, Paper, Folia; Velocity, BungeeCord | Ornithe b1.0–1.14.4; Poseidon b1.7.3 (1.1) | Poseidon done |

## 5. Decisions for the owner

- **D1 Metrics.** `server-common` reports to `moystats-production.up.railway.app` at start and every 30 minutes, on by
  default: server UUID, player count, OS, Java version, cores, LVC and game version. Keep (opt-in?) or remove.
- **D2 Versions.** Answered: the last release of each minor version, not b1.8.1 (2.11).
- **D3 Protocol.** Bumped to 2.0.0 (2.3): clients from Modrinth (LVC 0.1.x on OSL gen1) can't work with gen2 anyway,
  and now they get a clear log line instead of a broken session. Say if it should stay 1.x.

## 6. Testing

- b1.7.3 loop used for the port: a Knot-launched Ornithe b1.7.3 server (offline mode, OSL 0.24.0, LVC server jar) and
  a Knot client (Altrender, OSL, Mod Menu, LegacyFix, LVC client) passed `--server/--port`. Metrics were switched off
  first (`config/moyStats/config.yml`, `enabled: false`), and the client ran without audio devices (empty ALSA config)
  so no microphone was opened.
- Every version (2.10): Loom's dev runs (`runServer`, `runClient` joining it) for the merged versions; for the
  split ones (b1.x, 1.0.0–1.2.5) the release jars in a Knot server and client, because their dev servers don't start
  (an OSL mixin target is missing from the server-only dev mappings). The tests run on Java 21, so: 1.3–1.5 need
  `-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote` (Bouncy Castle class), 1.7.4–1.8.x clients
  `--userProperties {}` (launchers pass it), and 1.8.8+ the plain transport (`use-native-transport=false`,
  `useNativeTransport:false`: Netty 4.0's epoll fails on Java 21). Players on Java 8 need none of this.
- Audio needs two clients and real devices: by the owner, or with the owner's OK.
