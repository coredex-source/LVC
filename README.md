[Modrinth](https://modrinth.com/plugin/legacyvoicechat)

> [!CAUTION]
> I used AI to help merge the 19 old branches into one single project so it's easier to update going forward. Not everything is tested, and some of it still needs to be verified and cleaned up.


## Supported
- Ornithe (gen2, OSL 0.24.0+): client and server mods for b1.0, b1.4_01, b1.7.3, b1.8, 1.0.0, 1.1, 1.2.5 (split
  client/server jars) and 1.3.1, 1.3.2, 1.4.7, 1.5.1, 1.5.2, 1.6.1, 1.6.4, 1.7.2, 1.7.4, 1.7.10, 1.8, 1.8.8, 1.8.9,
  1.9.4, 1.10.2, 1.11.2, 1.12.2, 1.13, 1.14.4 (one jar). Client and server need the same LVC protocol (2.x).
  b1.0 servers don't start with OSL 0.24.0 (an OSL issue), so b1.0 is untested.
- Project Poseidon (Beta 1.7.3 servers): Bukkit plugin.

## Building
`./gradlew build` (Gradle 9.8 on Java 21 or newer; the jars are Java 8 bytecode). Jars land in `build/dist/`.
See `development.md` for the plan and known bugs.


## Known Bugs / Reports
I've been away from this project for awhile and haven't gone around to some stuff or updated old branches for version v0.1.1 and haven't fixed it here. Their is some stuff that is known to be broken.
(Bugs are not for all versions / loaders)
- Some rendering in the newer versions
- (Not verified) Been a while and i know i fixed it for 0.1.1 but haven't checked the code here but was reports of audio clicking sometimes when rotating your head
- Leaving a world and rejoining can stop game audio
- Game crashes when you press ESC while GUI is open
- Some versions using a different packet id because me being dumb didn't think people would use a certain loader with a certain server software, opps.
- Probably tons more, mod was made to be somewhat functional and refined later but life got in the way.

