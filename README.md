# LiquidBounce Extras

Modules and HUD components that [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) does not ship. The code is proudly hallucinated by Claude Opus 5.5, GPT-6 (Astra) and deepseek-flash. In my opinion, for such an add-on, this is fine. I would not recommend making AI write module code for an actual client; however, Claude does kind of well with game testing. Its idea is to write simplistic code to give you an idea of how to write your own add-on.

![ClickGUI](docs/clickgui.png)

Run `.marketplace subscribe 781` in the client, or drop the jar from the
[releases](https://github.com/CCBlueX/LiquidBounce-Addon-Extras/releases) into `mods/`.

## Modules

### Base Hunting

| Module | What it does | Screenshot |
|---|---|---|
| BaseFinder | Reports chunks with player-made blocks, workstations, placed entities, written signs or traded villagers |  |
| StashFinder | Reports chunks with many containers and follows them as they change; trial chambers are left out | [View](docs/stashfinder.png) |
| SuspiciousBlockDetector | Reports doors, trapdoors, chests, beds, obsidian and metal blocks that appear near you | [View](docs/suspiciousblockdetector.png) |
| CollectibleESP | Highlights valuables in item frames, filled maps and banners | [View](docs/collectibleesp.png) |
| PortalFinder | Reports and marks lit Nether and End portals | [View](docs/portalfinder.png) |
| CaveDisturbanceDetector | Marks plain air in cave walls, where someone mined into a cave |  |
| TunnelTrailESP | Highlights dug tunnels, staircases and one-wide shafts | [View](docs/tunneltrailesp.png) |
| SoundLocator | Marks where explosions, containers, portals or sounds you pick were played | [View](docs/soundlocator.png) |

### Grinding

| Module | What it does | Screenshot |
|---|---|---|
| AutoJump (Java) | Jumps always, while moving or while sprinting |  |
| AutoShearer | Shears sheep in reach without changing your visible slot | [View](docs/autoshearer.png) |
| AutoSign (Java) | Writes new signs with your last text or a template, without opening the editor | [View](docs/autosign.png) |
| AutoAnvilRepair | Combines damaged items in an open anvil up to a level cost; enchanted items are never sacrificed | [View](docs/autoanvilrepair.png) |
| AutoSmelter | Keeps an open furnace, blast furnace or smoker emptied, loaded and fuelled | [View](docs/autosmelter.png) |
| AutoBreed | Feeds adult animals in reach that have a partner nearby | [View](docs/autobreed.png) |

### QoL

| Module | What it does | Screenshot |
|---|---|---|
| LightOverlay | Marks where hostile mobs can spawn: red now, yellow once it is dark | [View](docs/lightoverlay.png) |
| Waypoints | Waypoints per server and dimension, listed with distance and direction, the selected one marked | [View](docs/waypoints.png) |
| NoItemBounce | Stops hotbar items from bouncing when they change |  |

### PvP

| Module | What it does | Screenshot |
|---|---|---|
| AnchorAura | Places, charges and sets off respawn anchors at targets and pearl landings, within damage limits | [View](docs/anchoraura.png) |
| AnchorMacro | Places, charges and sets off an anchor where you look while the bind is held |  |
| BlastCover | Puts obsidian or glowstone between you and a charged anchor before it goes off |  |
| CrystalMacro | Places obsidian and a crystal where you look and breaks it |  |
| TotemSwitch | Holds a hotbar totem while the next hit could kill you | [View](docs/totemswitch.png) |
| MisplayGuard | Cancels your own losing clicks, such as glowstone into a charged anchor |  |
| BucketSteal | Scoops or plugs water and lava next to your enemy |  |
| CartBomb | Lays and shoots TNT minecarts where you look or where your shot lands |  |
| PearlCart | Meets an enemy pearl landing with a TNT minecart |  |
| CartBreaker | Breaks unlit TNT minecarts others put down |  |
| CartCover | Places blocks between you and a lit TNT minecart |  |
| SwingRedirect | Turns a swing at the air onto the closest target near the crosshair | [View](docs/swingredirect.png) |
| HitHold | Holds a swing until the target can take it |  |
| HitFlick | Turns away for one tick before each hit |  |
| CrossbowLoader | Loads a crossbow from anywhere in the inventory |  |
| ShieldSwap | Blocks with a shield from anywhere in the inventory |  |
| ShieldGuard | Raises a shield when a close enemy can swing again |  |
| MaceAssist | Holds the best mace while you fall onto an enemy |  |
| BreachSwap | Swings a Breach mace instead of the sword now and then |  |
| ElytraDive | Takes off with an elytra and dives again after a mace hit |  |
| ShieldDrain | Hits a raised shield several times a tick |  |
| SpearAssist | Stabs beyond reach, follows charge hits with the mace and shows the charge damage | [View](docs/spearassist.png) |
| InstantLunge | Lunges once with your best Lunge spear |  |
| MacePrediction | Shows where the nearest enemy in the air comes down, with your reach around it | [View](docs/maceprediction.png) |
| PearlLaunch | Throws a wind charge the moment a pearl lands you |  |
| WindGlide | Keeps an elytra glide up with wind charges | [View](docs/windglide.png) |
| PearlHop | Hops with a pearl thrown at your feet |  |
| PearlCatch | Catches your own pearl mid-air with a wind charge |  |

### Extras

| Module | What it does | Screenshot |
|---|---|---|
| MessageAura (Java) | Whispers a message to every player who comes into view |  |
| PacketCanceller | Drops the packets you pick, in either direction |  |
| IntruderAlert | Reports players who are not your friends as they come near | [View](docs/intruderalert.png) |
| AntiTrap | Keeps bait entities out of your world and refuses sign editors you did not open |  |
| Disguise | Shows the nearest stranger under another name, rank and skin | [View](docs/disguise.png) |

## HUD components

| Component | What it shows | Screenshot |
|---|---|---|
| FPS, TPS, Ping | The value coloured good, ok or bad, over a twelve second graph | [View](docs/hud.png) |
| Player, CPS, Biome, Coordinates, Speedometer | One value each, with its own prefix, suffix, colour and background | [View](docs/hud.png) |
| Clock | The time, with a blinking colon and how far the minute has run | [View](docs/clock.png) |
| CartKit | Rails, TNT minecarts, bow draw and live carts nearby | [View](docs/cartkit.png) |
| Resources | Totems, golden apples, crystals and bottles; warns once one falls below its limit | [View](docs/resources.png) |

## Commands

| Command | What it does | Screenshot |
|---|---|---|
| `.where [share]` | Prints your position, or says it in the server chat | [View](docs/where.png) |
| `.waypoint add/remove/list/select/deselect` | Manages the waypoints | |
| `.findings list/show/forget/waypoint` | Manages what BaseFinder and StashFinder found | |

Waypoints and findings are saved in `LiquidBounce/extras-worlds/`.

## Building and testing

```sh
./gradlew build
./gradlew runClientGameTest
```

Most game tests join the shared [game test server](https://github.com/CCBlueX/gametest), a Paper 26.3
server with [an anticheat of choice](https://github.com/CCBlueX/gametest/blob/main/docs/anticheats.md), and fails
when the server objects to anything the client did. [AGENTS.md](AGENTS.md) explains how.

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
| `modules/qol/ModuleLightOverlay.kt` | Spawn rule | [Meteor Client `BlockUtils`](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/utils/world/BlockUtils.java#L313-L338) | Derived code, GPL-3.0 |
| `modules/basehunting/ModuleStashFinder.kt` | Trial chamber filter | [Meteor Client `StashFinder`](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/systems/modules/world/StashFinder.java#L64-L73) | Idea and block list, GPL-3.0 |
| `modules/basehunting/ModuleCaveDisturbanceDetector.kt` | Plain air next to cave air | [Trouser-Streak `CaveDisturbanceDetector`](https://github.com/etianl/Trouser-Streak/blob/0cf3231b56c25773cd66aa21931470bdf8799c9e/src/main/java/pwn/noobs/trouserstreak/modules/CaveDisturbanceDetector.java) | Idea |
| `modules/basehunting/ModuleBaseFinder.kt` | Written signs and traded villagers | [Trouser-Streak `BaseFinder`](https://github.com/etianl/Trouser-Streak/blob/be39bf88955d91261471a3462af4ef8a5f18b9f4/src/main/java/pwn/noobs/trouserstreak/modules/BaseFinder.java) | Ideas |

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
