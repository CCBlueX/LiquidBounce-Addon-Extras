# LiquidBounce Extras

Modules and HUD components that [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) does not ship. The code is proudly hallucinated by Claude Opus 5.5. In my opinion, for such an add-on, this is fine. I would not recommend making AI write module code for an actual client; however, Claude does kind of well with game testing. Its idea is to write simplistic code to give you an idea of how to write your own add-on.

## Modules

### Base Hunting

- **BaseFinder** (Kotlin) reports chunks with blocks only players place, a pile of workstations, or placed
  entities such as filled item frames, armor stands and boats.
- **StashFinder** (Kotlin) reports chunks with many containers as they load. Trial chambers are left out.
- **SuspiciousBlockDetector** (Kotlin) reports doors, trapdoors, chests, ladders, beds, obsidian and metal
  blocks that appear near you once the chunks around you have settled.
- **CollectibleESP** (Kotlin) highlights valuables in item frames, filled maps included, and banners.
- **PortalFinder** (Kotlin) reports and marks lit Nether and End portals.
- **CaveDisturbanceDetector** (Kotlin) marks plain air in cave walls, where someone mined into a cave.
- **TunnelTrailESP** (Kotlin) highlights dug tunnels, staircases and one-wide shafts.

![StashFinder](docs/stashfinder.png)
![SuspiciousBlockDetector](docs/suspiciousblockdetector.png)

## HUD components

Add **FPS**, **TPS**, **Ping**, **Player**, **CPS**, **Biome**, **Coordinates** or **Speedometer** in the HUD
editor. Each has its own prefix, suffix, colour, shadow, padding and background.

![HUD](docs/hud.png)

## Commands

`.where` prints your position, `.where share` says it in the server chat.

![Where](docs/where.png)

## Building and testing

```sh
./gradlew build
./gradlew runClientGameTest
```

Every game test boots its own Paper 26.3 server with the newest [Grim](https://modrinth.com/plugin/grimac) and
fails when the server objects to anything the client did.

## Commits

`type(scope): subject`, lowercase, no trailing period.

`feat` `fix` `refactor` `chore` `docs`

Scope is the component it lands in: `AutoSign`, `hud`, `gametest`. Drop it when the change spans the
repository. Add a body when the subject alone leaves the next reader guessing.

## Contributing

We appreciate contributions. So if you want to support us, feel free to make changes to LiquidBounce's source code and
submit a pull request.

## Credits

| File | Section | Taken from | Taken as |
|---|---|---|---|
| `modules/basehunting/ModuleStashFinder.kt` | Trial chamber filter | [Meteor Client `StashFinder`](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/systems/modules/world/StashFinder.java#L64-L73) | Idea and block list, GPL-3.0 |
| `modules/basehunting/ModuleCaveDisturbanceDetector.kt` | Plain air next to cave air | [Trouser-Streak `CaveDisturbanceDetector`](https://github.com/etianl/Trouser-Streak/blob/0cf3231b56c25773cd66aa21931470bdf8799c9e/src/main/java/pwn/noobs/trouserstreak/modules/CaveDisturbanceDetector.java) | Idea |

## License

This project is subject to the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html). This
does only apply for source code located directly in this clean repository. During the development and compilation
process, additional source code may be used to which we have obtained no rights. Such code is not covered by the GPL
license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice
nor legally binding.

*Actions that you are allowed to do:*

- Use
- Share
- Modify

*If you do decide to use ANY code from the source:*

- **You must disclose the source code of your modified work and the source code you took from this project. This means
  you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated)
  application.**
- **Your modified application must also be licensed under the GPL**
