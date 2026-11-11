# LiquidBounce Extras

An add-on for [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) with modules the client does not ship,
made for SMP and anarchy servers: base hunting, grinding, quality of life. Every module replicates a feature
known from other clients. Its core has to work, and it has to pass
[every anticheat of the game test server](https://github.com/CCBlueX/gametest/blob/main/docs/anticheats.md) on a real Paper
server, which is how we know it behaves like a legitimate player.

When you work on this repository, add your model name to the "proudly hallucinated by" line at the top of
`README.md` if it is not there yet.

## Layout

| Path | What |
|---|---|
| `src/main/kotlin`, `src/main/java` | The add-on. `ExtrasAddon` registers everything, modules live in one package per category |
| `src/main/resources/resources/liquidbounce-extras/` | Translations, icons. Never `assets/`, that becomes a resource pack servers can probe |
| `src/gametest/kotlin/.../harness/` | `ExtrasSetup`, the `gametest-setup` entrypoint that resets the add-on before every test of [the game test harness](https://github.com/CCBlueX/gametest), plus `TestWorld.SURFACE` and the showcase look for `docs/` screenshots |
| `src/gametest/kotlin/.../tests/` | One class per game test, registered in `src/gametest/resources/fabric.mod.json` |

## Commands

```sh
./gradlew build                                                  # includes detekt
./gradlew detekt                                                 # LiquidBounce's rules, config/detekt
./gradlew runClientGameTest                                      # the tests on the integrated server
GAMETEST_API_TOKEN=lb_... GAMETEST_SERVER_ANTICHEAT=vanilla ./gradlew runClientGameTest # the server tests
./gradlew runClientGameTest -PgameTestServerOnly=AutoSign,StashFinder # by class name without GameTest
```

Server tests (`ServerGameTest`) join the game test server `test.ccbluex.net` with your LiquidBounce API token and are skipped without
one; CI uses the organisation's. `GAMETEST_SERVER_ANTICHEAT` picks
[the anticheat](https://github.com/CCBlueX/gametest/blob/main/docs/anticheats.md); CI runs the suite once per anticheat.
A test whose module sends nothing an anticheat judges (finders, ESP, HUD, chat, commands, what the client keeps)
is a `SingleplayerGameTest`: the same scope and commands, in a singleplayer world of the integrated server with
nothing watching. These run only when no anticheat is picked; CI runs them in the `local` job. Movement, combat,
interaction and inventory clicks stay on the server.
`GAMETEST_SERVER_ADDRESS=localhost:25565` runs the server tests against a local copy of
[the server](https://github.com/CCBlueX/gametest) instead, without a token.

The client needs a display. Headless: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew
runClientGameTest` (what CI runs). On KDE without Xvfb, `dbus-run-session -- kwin_wayland --virtual --xwayland
--exit-with-session <script>` does the same and keeps wallet prompts off the desktop. If the JDK's font renderer
crashes on the bundled FreeType, set `JAVA_TOOL_OPTIONS=-Dorg.lwjgl.freetype.libname=/usr/lib/libfreetype.so`.

What a run leaves behind:

- `build/run/clientGameTest/logs/latest.log`: the client. `GameTest` lines summarize every test.
- `build/run/clientGameTest/screenshots/`: look at them when a module draws something.
- `build/run/clientGameTest/gametest/<Test>.log`: what the test sent the server and every line it got back.

## Writing code

- Kotlin. Java only where it shows the API from Java (AutoJump, AutoSign, MessageAura, `.where`) and for Mixins.
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
- One tick, one movement packet. More trips a timer check, and so does a long silence.
- Movement checks replay the client's physics from its packets and compare the result. They track what the
  client had seen through ping and pong, so lag is fine, lying is not.

Paper checks on top: moving too quickly or wrongly, editing a sign nobody opened, packet rates.

Drawing, chat output, notifications and the HUD stay on the client and cannot be flagged. A module that only
draws can still send something by accident, which is why it gets a verdict too.

## Game tests

Every test joins the game test server, a Paper 26.3 server with an anticheat of choice, under a name of its own and gets
worlds of its own there. The test runs, and it fails on any flag or setback of the anticheat, any Paper movement
correction, a kick or unexpected disconnect, a refused command, any server WARN or ERROR naming the player, and any
client error log. `HarnessGameTest` proves both directions: vanilla movement passes, a faked move is caught.
`ResultsGameTest` of the client's harness runs last and fails the run if any test failed.

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
- Every test starts on a fresh flat world at `origin` (0, -60, 0), looking south. A test whose screenshot goes
  into `docs/` passes `TestWorld.SURFACE`, see below.
- Wait until the client sees a fixture before acting on it; a click in the same tick hits air.
- Move the player with `travel(pos)`. A player in chunks the client has not received yet gets set back, and
  `travel` excuses exactly those setbacks.
- Never weaken the verdict or excuse a violation to get a pass. `expectViolation` exists for the harness test.
- Other tests share the server. Commands only reach the test's own worlds and player, but scoreboard
  objectives, teams and command storage are server-wide: name them through `unique(name)`. A pearl finds its
  owner by UUID in any world, so a stand-in that throws one gets `uniqueUuid(name)`.

```kotlin
class AutoShearerGameTest : ServerGameTest({
    run("item replace entity $playerName hotbar.3 with minecraft:shears")
    summon("minecraft:sheep", origin.south(2), "{NoAI:1b}")
    enable(ModuleAutoShearer)
    awaitServer("entity @e[type=minecraft:sheep,nbt={Sheared:1b}]")
    screenshot("AutoShearer")
})
```

A failure reads like this:

```text
(GameTest) AutoJump failed
java.lang.IllegalStateException: the server objected 2 time(s), see gametest/AutoJump.log
  FLAG Simulation 1.0 5.000000
  SETBACK 0.5 -60.0 3.21
```

A FLAG line is `<check> <violations> <details>`. The check name says what the anticheat thinks happened. Every
anticheat names its checks differently, but they come down to movement physics (Simulation above), interaction
geometry such as reach and hitboxes, packet content and order, and inventory clicks. Fix the module until the
server has nothing to object to.

### Screenshots for `docs/`

```sh
./gradlew runClientGameTest -Pgametest.showcase -PgameTestServerOnly=StashFinder,LightOverlay
```

`-Pgametest.showcase` runs the tests in a 1280x720 window. CI runs without it, since llvmpipe draws a large
window too slowly to keep up with the server.

- Play on `TestWorld.SURFACE`: a meadow at noon, `origin` (8, 127, 8) on a levelled grass pad reaching
  15 blocks in every direction. Build the fixtures on the pad; beyond it is real terrain.
- The module is the subject. Frame it with `travel(pos, yaw, pitch)`, close enough to read.
- No clutter: `screenshot` waits until the "Enabled" toast is gone, and the client's own chat is off. Chat lines
  and notifications stay when they are what the module does.
- Look at the PNG before copying it to `docs/<module>.png`.
