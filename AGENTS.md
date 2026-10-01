# LiquidBounce Extras

An add-on for [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) with modules the client does not ship,
made for SMP and anarchy servers: base hunting, grinding, quality of life. Every module replicates a feature
known from other clients. Its core has to work, and it has to pass against Grim on a real Paper server,
which is how we know it behaves like a legitimate player.

When you work on this repository, add your model name to the "proudly hallucinated by" line at the top of
`README.md` if it is not there yet.

## Layout

| Path | What |
|---|---|
| `src/main/kotlin`, `src/main/java` | The add-on. `ExtrasAddon` registers everything, modules live in one package per category |
| `src/main/resources/resources/liquidbounce-extras/` | Translations, icons. Never `assets/`, that becomes a resource pack servers can probe |
| `src/gametest/kotlin/.../harness/` | The game test harness: Paper process, console, verdict, and the client and server actions tests use |
| `src/gametest/kotlin/.../tests/` | One class per game test, registered in `src/gametest/resources/fabric.mod.json` |
| `paper-probe/` | Paper plugin that reports every Grim flag, setback, movement correction, kick and quit |

## Commands

```sh
./gradlew build                                                  # includes detekt
./gradlew detekt                                                 # LiquidBounce's rules, config/detekt
./gradlew runClientGameTest                                      # every test, one Paper server each
./gradlew runClientGameTest -Pgametest.only=AutoSign,StashFinder # by class name without GameTest
./gradlew runClientGameTest -Ppaper.build=140 -Pgrim.version=2.3.74-61117c2
```

The client needs a display. Headless: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew
runClientGameTest` (what CI runs). On KDE without Xvfb, `dbus-run-session -- kwin_wayland --virtual --xwayland
--exit-with-session <script>` does the same and keeps wallet prompts off the desktop. If the JDK's font renderer
crashes on the bundled FreeType, set `JAVA_TOOL_OPTIONS=-Dorg.lwjgl.freetype.libname=/usr/lib/libfreetype.so`.

What a run leaves behind:

- `build/run/clientGameTest/logs/latest.log`: the client. `Extras/GameTest` lines summarize every test.
- `build/run/clientGameTest/screenshots/`: look at them when a module draws something.
- `build/paper/instances/<Test>/logs/latest.log`: the server's side, including every `[ExtrasProbe]` line.

## Writing code

- Kotlin. Java only where it shows the API from Java (AutoJump, AutoSign, MessageAura, `.where`), for Mixins,
  and in `paper-probe`, since Paper ships no Kotlin runtime.
- Modern language: Kotlin 2.4 with context parameters, explicit backing fields, guard conditions, collection
  literals and context-sensitive resolution; the return value checker runs on everything. Java 25 with records,
  pattern matching, `var` and `_`, in packages marked `@NullMarked` (JSpecify).
- Prefer APIs marked `@AddonApi`, they stay stable between client releases. The rest of the client is usable
  but may change under you.
- Before adding a module, search `features/module/modules` in the client. Duplicates of built-ins get removed.
- Categories are by purpose: Base Hunting, Grinding, QoL, and Extras for the rest. A new tab only once several
  modules share a purpose.
- Register in `ExtrasAddon`. Translate the module, every value and every chat message in `lang/en_us.json`.
- Every handler is a property named `...Handler`, never a call inside `init`:
  `@Suppress("unused") private val scanHandler = tickHandler { ... }` in Kotlin,
  `@SuppressWarnings("unused") private final AutoCloseable screenHandler = on(ScreenEvent.class, ...)` in Java.
- Waiting is the client's coroutines, never a counter or cooldown field: `waitTicks`, `waitSeconds`,
  `tickConditional` and `tickUntil` inside a `tickHandler` or `eventListenerScope.launch`, `after(ticks, ...)`
  and `every(ticks, ...)` from Java. They stop on their own when the module is disabled.
- State that tests or other code read is exposed through explicit backing fields, read-only from the outside.
- Watch blocks with a `BlockTracker` subscribed to `ChunkScanner` rather than looping over the world each tick.
  Its callbacks run on worker threads; hand results to the game thread with `mc.execute`.
- Container clicks go through `ScheduleInventoryActionEvent`, aiming through `RotationManager` or a rotation
  mode. Nothing may reach the server faster or in another order than a human's input would.
- Comments only for what the code cannot say: a protocol quirk, a reason, a credit. Write like a senior
  developer who is tired of typing.
- Credit only what you actually took: code, or an idea you would not have come up with yourself. That gets a
  comment at the section and a row in the README's Credits. Common sense (a delay, the nearest target, a
  marker that expires) gets nothing. Take code only from GPL-compatible projects, ideas at most from others.
- Before handing over, `./gradlew detekt` passes, and if an IDE is connected over MCP (JetBrains `lint_files`
  or `get_file_problems`), its inspections have nothing left to say about the files you touched. Fabric's
  `@ApiStatus.Experimental` notes on the game test API are expected.
- Commits follow the README.

## How Minecraft decides what is real

The server owns the game. The client keeps a copy fed by packets and predicts a few things so the game feels
instant. Whatever the server does not accept never happened.

| State | Owner | The client |
|---|---|---|
| Blocks and block entities | Server | Predicts its own placing and breaking; the server acknowledges or corrects |
| Entities, health, a sheep's wool | Server | Shows what it receives |
| Inventory, containers, XP, game mode | Server | Predicts clicks; a mismatch makes the server resend everything |
| Own position, rotation, on ground | Client, checked | Simulates physics and reports it; the server teleports it back when it disagrees |
| Chat and commands | Server | Sends text. LiquidBounce's `.commands` never leave the client |

So changing that state on the client only changes the copy:

- `gameMode.setLocalMode(CREATIVE)` changes what the client draws. The server still sees survival, refuses
  every creative inventory packet, gives you no items, and sets you back when you fly.
- `inventory.setItem`, `level.setBlock` and `level.removeEntity` on the client are overwritten by the next
  packet. Until then the client acts on things the server does not have, and those actions get rejected or
  flagged.
- A test fixture made on the client proves nothing. Make it on the server, then check that the client sees it.

Every action is a packet, and order and timing are part of it:

- Rotation travels with movement. An interaction has to come after a movement packet that already faces its
  target, or the hit is geometrically impossible. Rotation modes run the action once the rotation went out.
- A silent slot switch is its own packet and has to arrive before the use.
- Container clicks carry the predicted result. Clicking while moving or sprinting, or without the container
  open, is what inventory checks catch.
- One tick, one movement packet. More is Timer, a long silence is NegativeTimer.
- Grim replays the client's physics from its packets and compares the result (Simulation). It tracks what the
  client had seen through ping and pong, so lag is fine, lying is not.

Paper checks on top: moving too quickly or wrongly, editing a sign nobody opened, packet rates.

Drawing, chat output, notifications and the HUD stay on the client and cannot be flagged. A module that only
draws can still send something by accident, which is why it gets a verdict too.

## Game tests

Each test class boots its own Paper 26.3 server with the newest Grim, downloaded and cached in `build/paper`,
plus `paper-probe`. The client joins, the test runs, and the test fails on any Grim flag or setback, any Paper
movement correction, a kick or unexpected quit, any server WARN or ERROR naming the player, and any client
error log. `HarnessGameTest` proves both directions: vanilla movement passes, a faked move is caught.
`ResultsGameTest` runs last and fails the run if any test failed.

What a module's test covers:

1. The trigger, built through the server console: `setBlock`, `fill`, `summon`, `give`, `run(...)`.
2. What the client shows: findings, chat lines.
3. What the server says, whenever the module acts: `awaitServer("entity @e[...]")`, `awaitAnswer("data get
   ...", expected)`, `query(...)`. A client-side check alone proves nothing for a module that acts.
4. The edge that keeps it safe: friends ignored, cost limits kept, an opened door is no new door.
5. Disabling clears its state.
6. A screenshot when it draws something. Copy it to `docs/` when the README shows it.

The verdict itself covers "no violations".

Rules:

- Real input (`input.holdKey`, `pressMouse`, `lookAt`, `typeChars`) over calling the game directly.
- Never block the test thread. Between ticks the client is frozen and the server sees a lagging player. The
  scope's `await` helpers tick while they wait; there is no `Thread.sleep`.
- No fixtures made on the client, with one exception: a `RemotePlayer` for modules that only watch players,
  because the server cannot provide a second player. Keep it out of reach.
- Every test starts on a fresh flat world at `origin` (0, -60, 0), looking south.
- Wait until the client sees a fixture before acting on it; a click in the same tick hits air.
- Move the player with `travel(pos)`. Grim holds a player in chunks the client has not received yet by
  setting them back, and `travel` excuses exactly those setbacks.
- Never weaken the verdict or excuse a violation to get a pass. `expectViolation` exists for the harness test.

```kotlin
class AutoShearerGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.3 with minecraft:shears")
    summon("minecraft:sheep", origin.south(2), "{NoAI:1b}")
    enable(ModuleAutoShearer)
    awaitServer("entity @e[type=minecraft:sheep,nbt={Sheared:1b}]")
    screenshot("AutoShearer")
})
```

A failure reads like this:

```text
(Extras/GameTest) AutoJump failed
java.lang.IllegalStateException: the server objected 2 time(s), see build/paper/instances/AutoJump/logs/latest.log
  [INFO] [ExtrasProbe] FLAG Player0 Simulation 1.0 5.000000
  [INFO] [ExtrasProbe] SETBACK Player0 0.5 -60.0 3.21
```

A FLAG line is `<player> <check> <violations> <details>`. The check name says what Grim thinks happened:
Simulation is movement physics, Reach and Hitboxes are interaction geometry, BadPackets and PacketOrder are
content and order, the inventory checks are clicks. Fix the module until the server has nothing to object to.
