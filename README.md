# Quakechasm - Spigot Compatibility Port

## IMPORTANT NOTICE - UNSUPPORTED VERSION

**This is a Spigot-compatible port of Quakechasm created specifically for distribution on SpigotMC.org.**

### Spigot Version Limitations
- NO SUPPORT will be provided for this Spigot version
- NOT RECOMMENDED for production use
- STRONGLY RECOMMENDED to switch to [Paper](https://papermc.io/) and use the supported version
- This port may have reduced functionality and degraded performance compared to the Paper version
- Some features may not work correctly due to Spigot API limitations
- Chat functionality uses legacy format conversion and may not display as intended
- Some Adventure API features are emulated and may have visual differences
- Performance may be lower than the Paper version
- Future updates will focus on the Paper version only

### Paper Version Differences
- Full feature support with all intended functionality
- Better performance and optimization
- Active support and bug fixes
- Modern API features for enhanced gameplay
- Regular updates and improvements

**Supported Paper Version:** https://github.com/polyzium/quakechasm/tree/master

---

## The Next-Generation Evolution of Quake in Minecraft

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue)](https://www.gnu.org/licenses/agpl-3.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)](https://spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)

Quakechasm (formerly DarkChronics-Quake) is a feature-rich implementation of the Quake minigame for Minecraft servers. Built from the ground up with versatility and entertainment value at its core, Quakechasm brings the authentic Quake experience to Minecraft like never before.

## Why Quakechasm over other Quake plugins?

While other Quake plugins take inspiration from Hypixel's simplified interpretation, Quakechasm draws directly from id Software's legendary Quake series, specifically Quake 3 Arena and Quake Live. This isn't just another instagib clone; it's a faithful recreation of classic arena FPS gameplay in Minecraft.

Quakechasm implements mechanics from Quake 3/Live that are not found in any other Minecraft Quake plugin:

- 7 iconic weapons from the Quake lineup, such as the Rocket Launcher, Railgun, and BFG (unlike the instagib-focused approach of other plugins)
- 3 powerups, including the iconic Quad Damage, that give your player buffs or defensive stats for a short period of time
- Health and armor pickups for additional protection, with factors based off of Quake 3 itself
- Mobility mechanics such as Slipgate teleporters, jumppads and rocketjumping
- 3 distinct game modes: Free For All, Team Deathmatch, and Capture the Flag
- Custom HUD with weapon info, ammo counter, and powerup timers
- Visual particles and customizable sounds for extra flavor
- Multi-team spawnpoint system for different game modes
- Custom death messages inspired by the Quake games
- Bunnyhopping/strafejumping (experimental)
- ... and potentially more to come!

**This plugin is under development.** Expect bugs and stability issues as Quakechasm is being refined and expanded. Your feedback is appreciated!

## Requirements

- Spigot 1.21.8 or newer (Paper 1.21.8+ STRONGLY RECOMMENDED instead)
- Java 21 or higher
- Dependencies:
  - [CommandAPI](https://github.com/JorelAli/CommandAPI) for commands
  - [WorldEdit](https://enginehub.org/worldedit) for map creation and management

## Installation

### RECOMMENDED: Use Paper Instead
For the best experience, using [Paper](https://papermc.io/) with the [supported Quakechasm version for Paper](https://github.com/polyzium/quakechasm/tree/master) is strongly recommended.

### Spigot Installation (Not Recommended):
1. Install the required dependencies (see above).
2. Download the latest `quakechasm-<version>-spigot.jar` from SpigotMC (or build it yourself) and drop it into the server's `plugins/` folder.
3. Start or restart the server. Quakechasm will create `plugins/Quakechasm/config.json` and other data files on first launch.
4. Resource pack:
   - The plugin expects a custom resource pack for weapon models, HUD elements, sounds, etc.
   - [A sample pack](http://tilde.club/~polyzium/resourcepack.zip) is provided for reference, but because it contains Quake 3-inspired assets you should assemble your own pack before going live. Weapon models are released under the CC BY-NC-SA 4.0 license.
   - Distribute the finished pack using your usual workflow (`resource-pack` in `server.properties`, link prompt plugins, etc.).

## Building (optional)

You only need these steps if you prefer compiling the plugin yourself.

1. Install JDK 21+ and Git.
2. Clone the repository.
3. Run `./gradlew build` (or `gradlew.bat build` on Windows) to produce `build/libs/quakechasm-<version>-spigot.jar`.

## Contributing

Contributions are welcome. Please feel free to submit pull requests or open issues for bugs and feature requests.

## License

This project is licensed under the GNU Affero General Public License v3 - see the [LICENSE](LICENSE) file for details.

## Thanks to

theblurry99, KagiSame, Raov, GMMD for testing