package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.uniqueUuid
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.liquidbounce.gametest.harness.configure
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.gametest.harness.action.setBlock
import net.ccbluex.liquidbounce.gametest.harness.setting
import net.ccbluex.gametest.harness.action.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleAnchorAura
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleAnchorMacro
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBlastCover
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.platform.InputConstants

private const val TARGET = "@e[type=minecraft:zombie,limit=1]"
private const val TARGET_HEALTH = 300f
private const val PLAYER_HEALTH = 20f

// What the zombie stand-in is called in an ender pearl's Owner
private val GameTestScope.targetUuid get() = uniqueUuid("target")

class BlastCoverGameTest : ServerGameTest({
    arena()
    run("item replace entity $playerName hotbar.2 with minecraft:glowstone 4")
    run("item replace entity $playerName hotbar.3 with minecraft:obsidian 4")
    val anchor = origin.south(3)
    val cell = origin.south(2)
    chargedAnchor(anchor)
    enable(ModuleBlastCover)

    // Your own click on a charged anchor waits until the obsidian stands between you
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(cell, "minecraft:obsidian"))
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=1]"))
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitAnswer("execute unless ${block(anchor, "minecraft:respawn_anchor")}", "Test passed")
    check(health(playerName) >= PLAYER_HEALTH - 2) { "the covered blast hurt too much" }

    // On the crosshair, the cover waits for a look at the floor in front of the anchor
    clearArena()
    chargedAnchor(anchor)
    client {
        ModuleBlastCover.setting<Any>("Mode").setByString("Crosshair")
        ModuleBlastCover.setting<Int>("Timeout").set(60)
    }
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    ticks(2)
    check(client { ModuleBlastCover.pending } == anchor) { "the detonation was not held back" }
    awaitServer(block(cell, "minecraft:air"))
    lookAt(Vec3(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5))
    awaitServer(block(cell, "minecraft:obsidian"))
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitAnswer("execute unless ${block(anchor, "minecraft:respawn_anchor")}", "Test passed")
    check(health(playerName) >= PLAYER_HEALTH - 4) { "the covered blasts hurt too much" }

    // A blast leaves fire behind; a look at fire in the cell counts, the cover goes in its place
    clearArena()
    chargedAnchor(anchor)
    setBlock(cell, "minecraft:fire")
    awaitClient("the fire") { it.level!!.getBlockState(cell).`is`(Blocks.FIRE) }
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    ticks(2)
    check(client { ModuleBlastCover.pending } == anchor) { "the detonation was not held back" }
    lookAt(Vec3(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5))
    awaitServer(block(cell, "minecraft:obsidian"))

    // Glowstone charges, it does not set anything off
    clearArena()
    chargedAnchor(anchor)
    input.pressKey { it.keyHotbarSlots[2] }
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=2]"))
    awaitServer(block(cell, "minecraft:air"))

    // Before charge: the cover goes up before the glowstone goes in
    clearArena()
    // The count of covers goes with the anchor that was there
    awaitClient("the old anchor to be gone") { !it.level!!.getBlockState(anchor).`is`(Blocks.RESPAWN_ANCHOR) }
    ticks(2)
    setBlock(anchor, "minecraft:respawn_anchor")
    awaitClient("the empty anchor") { it.level!!.getBlockState(anchor).`is`(Blocks.RESPAWN_ANCHOR) }
    client {
        ModuleBlastCover.setting<Any>("Mode").setByString("Rotate")
        ModuleBlastCover.setting<Any>("Timing").setByString("BeforeCharge")
        ModuleBlastCover.setting<Int>("Limit").set(1)
    }
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(cell, "minecraft:obsidian"))
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=0]"))
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=1]"))

    // Limit: this anchor had its one cover, the next charge goes in without
    setBlock(cell, "minecraft:air")
    awaitClient("the cover to be gone") { it.level!!.getBlockState(cell).isAir }
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=2]"))
    awaitServer(block(cell, "minecraft:air"))

    // Above MaxDamage a cover is not worth it
    client {
        ModuleBlastCover.setting<Int>("Limit").set(0)
        ModuleBlastCover.setting<Float>("MinDamage").set(0f)
        ModuleBlastCover.setting<Float>("MaxDamage").set(1f)
    }
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=3]"))
    awaitServer(block(cell, "minecraft:air"))
    disable(ModuleBlastCover)
    check(client { ModuleBlastCover.pending } == null) { "disabling kept a pending cover" }
})

class AnchorAuraGameTest : ServerGameTest(TestWorld.SURFACE, {
    arena()
    run("item replace entity $playerName hotbar.1 with minecraft:respawn_anchor 8")
    run("item replace entity $playerName hotbar.2 with minecraft:glowstone 16")
    run("item replace entity $playerName hotbar.3 with minecraft:obsidian 16")
    summonTarget(origin.south(4))
    val module = ModuleAnchorAura
    // One step every four ticks, so the test can stop it between two blasts
    client {
        for (stage in listOf("PlaceDelay", "ChargeDelay", "DetonateDelay")) {
            module.setting<IntRange>(stage).set(4..4)
        }
    }

    // Without a cover, every spot next to the target would hurt more than MaxSelfDamage
    configure(module, "Cover" to false)
    enable(module)
    ticks(40)
    awaitServer("items entity $playerName hotbar.1 minecraft:respawn_anchor[count=8]")
    check(client { module.plan } == null) { "planned a blast it cannot stand" }

    configure(module, "Cover" to true)
    awaitClient("the blast to hit", timeout = 400) { target(it).hurtTime > 0 }
    disable(module)
    val hit = awaitHealthBelow(TARGET, TARGET_HEALTH)
    awaitServer("items entity $playerName hotbar.1 minecraft:respawn_anchor[count=7]")
    awaitServer("items entity $playerName hotbar.3 minecraft:obsidian[count=15]")
    check(health(playerName) >= PLAYER_HEALTH - 4) { "the blast hurt more than MaxSelfDamage" }

    // An enemy pearl: the anchor waits charged where it comes down
    clearArena()
    // Out of reach, in the air above the pad; without AI it stays there
    run("tp $TARGET ${origin.x + 0.5} ${origin.y + 20} ${origin.z + 14.5}")
    awaitClient("the target to leave") { minecraft ->
        target(minecraft).z > origin.z + 10
    }
    enable(module)
    // Long enough for the Enabled toast to fade, so the screenshot need not wait while the pearl flies
    ticks(100)
    // Eight seconds up and down, so the checks below are done before it lands even on a slow client
    summon("minecraft:ender_pearl", origin.offset(0, 5, 3), "{Motion:[0.0d,3.0d,0.0d],Owner:$targetUuid}")
    awaitClient("a pearl landing to plan for") { module.plan?.landing != null }
    val anchor = client { module.plan!!.anchor }
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=1]"))

    // Guarded: glowstone in hand, a click on the waiting anchor does nothing
    input.pressKey { it.keyHotbarSlots[2] }
    lookAtTop(anchor)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    ticks(4)
    awaitServer(block(anchor, "minecraft:respawn_anchor[charges=1]"))
    awaitServer("items entity $playerName hotbar.2 minecraft:glowstone[count=14]")
    input.pressKey { it.keyHotbarSlots[0] }

    // From behind: the cover, the anchor and the landing with its countdown
    lookAt(Vec3(anchor.x + 0.5, anchor.y.toDouble(), anchor.z + 0.5))
    input.pressKey { it.keyTogglePerspective }
    screenshot("AnchorAura")
    repeat(2) { input.pressKey { it.keyTogglePerspective } }

    awaitClient("the thrower to be hit", timeout = 400) { target(it).hurtTime > 0 }
    disable(module)
    awaitHealthBelow(TARGET, hit)
    awaitAnswer("execute unless ${block(anchor, "minecraft:respawn_anchor")}", "Test passed")
    check(health(playerName) >= PLAYER_HEALTH - 4) { "the blast hurt more than MaxSelfDamage" }
    check(client { module.plan == null && module.landings.isEmpty() }) { "disabling kept a plan" }

    // OnlyExisting: nothing new goes down, an anchor already standing is charged and set off
    clearArena()
    run("tp $TARGET ${origin.x + 0.5} ${origin.y} ${origin.z + 4.5}")
    awaitClient("the target to come back") { target(it).z < origin.z + 5 }
    val anchors = client { it.player!!.inventory.getItem(1).count }
    val before = health(TARGET)
    configure(module, "OnlyExisting" to true)
    enable(module)
    ticks(40)
    check(client { module.plan } == null) { "planned without a standing anchor" }
    val existing = origin.south(3)
    setBlock(existing, "minecraft:respawn_anchor")
    awaitClient("the standing anchor to hit", timeout = 400) { target(it).hurtTime > 0 }
    disable(module)
    awaitHealthBelow(TARGET, before)
    awaitAnswer("execute unless ${block(existing, "minecraft:respawn_anchor")}", "Test passed")
    check(client { it.player!!.inventory.getItem(1).count } == anchors) { "OnlyExisting placed an anchor" }
})

class AnchorMacroGameTest : ServerGameTest({
    arena()
    run("item replace entity $playerName hotbar.1 with minecraft:respawn_anchor 8")
    run("item replace entity $playerName hotbar.2 with minecraft:glowstone 16")
    run("item replace entity $playerName hotbar.3 with minecraft:obsidian 16")
    summonTarget(origin.south(4))
    val spot = origin.south(3)
    val module = ModuleAnchorMacro
    val key = InputConstants.KEY_V
    client {
        module.bindValue.set(InputBind(InputConstants.Type.KEYBOARD, key, InputBind.BindAction.HOLD))
        module.setting<Any>("Switch").setByString("Visible")
        module.setting<IntRange>("Delay").set(4..4)
        (module.containedValues.first { it.name == "DoubleAnchor" } as ValueGroup).setting<Boolean>("Enabled").set(true)
    }
    // Glowstone breaks in the blast, so the spot is in reach again for the second anchor
    configure(ModuleBlastCover, "Block" to "Glowstone")
    enable(ModuleBlastCover)

    // OnlyOwn: someone else's anchor stays as it is
    chargedAnchor(spot)
    lookAtTop(spot)
    input.holdKeyFor(key, 30)
    awaitServer(block(spot, "minecraft:respawn_anchor[charges=1]"))
    awaitServer("items entity $playerName hotbar.2 minecraft:glowstone[count=16]")
    setBlock(spot, "minecraft:air")

    // Place where you look, cover, charge, blast, and the second one once the target can be hurt again
    lookAt(Vec3(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5))
    input.holdKey(key)
    awaitClient("the first blast to hit") { target(it).hurtTime > 0 }
    awaitClient("the target to recover") { target(it).hurtTime == 0 }
    awaitClient("the second blast to hit") { target(it).hurtTime > 0 }
    input.releaseKey(key)
    awaitClient("the macro to let go") { !module.enabled }
    awaitServer("items entity $playerName hotbar.1 minecraft:respawn_anchor[count=6]")
    // Two charges, two covers
    awaitServer("items entity $playerName hotbar.2 minecraft:glowstone[count=12]")
    check(health(TARGET) < TARGET_HEALTH) { "the target took no damage" }
    check(health(playerName) >= PLAYER_HEALTH - 4) { "the covered blasts hurt too much" }
    check(client { it.player!!.inventory.selectedSlot } == 0) { "the visible switch did not go back" }

    // BlastCover's Before charge timing: the cover stands before the first glowstone goes in
    clearArena()
    awaitClient("the arena to be clear") { it.level!!.getBlockState(spot).isAir }
    configure(ModuleBlastCover, "Timing" to "BeforeCharge")
    lookAt(Vec3(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5))
    var coveredAtCharge = false
    input.holdKey(key)
    awaitClient("the first charge") { minecraft ->
        val level = minecraft.level!!
        val state = level.getBlockState(spot)
        val charged = state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
        if (charged) {
            coveredAtCharge = Direction.entries.filter { it != Direction.DOWN }
                .any { level.getBlockState(spot.relative(it)).`is`(Blocks.GLOWSTONE) }
        }
        charged
    }
    input.releaseKey(key)
    awaitClient("the macro to let go") { !module.enabled }
    check(coveredAtCharge) { "the anchor was charged before its cover went up" }
})

private fun GameTestScope.arena() {
    // Peaceful takes the zombie away and scales blast damage to players down to nothing
    run("difficulty normal")
    run("effect give $playerName minecraft:fire_resistance infinite 0 true")
    // Obsidian stays put under blasts, so every spot keeps its floor
    fill(origin.offset(-6, -1, -4), origin.offset(6, -1, 10), "minecraft:obsidian")
}

private fun GameTestScope.clearArena() = fill(origin.offset(-5, 0, 1), origin.offset(5, 4, 9), "minecraft:air")

private fun GameTestScope.chargedAnchor(pos: BlockPos) {
    setBlock(pos, "minecraft:respawn_anchor[charges=1]")
    awaitClient("the anchor") { it.level!!.getBlockState(pos).`is`(Blocks.RESPAWN_ANCHOR) }
}

/** A zombie that stands still, has health for a few blasts and does not burn. */
private fun GameTestScope.summonTarget(pos: BlockPos) {
    val health = TARGET_HEALTH.toInt()
    summon(
        "minecraft:zombie", pos,
        "{NoAI:1b,PersistenceRequired:1b,UUID:$targetUuid,Health:${health}f," +
            "attributes:[{id:\"minecraft:max_health\",base:$health}]," +
            "equipment:{head:{id:\"minecraft:iron_helmet\",count:1}}," +
            "active_effects:[{id:\"minecraft:fire_resistance\",duration:-1}]}",
    )
    awaitClient("the target and the items") { minecraft ->
        val player = minecraft.player!!
        player.inventory.getItem(2).`is`(Items.GLOWSTONE) &&
            minecraft.level!!.getEntitiesOfClass(Zombie::class.java, player.boundingBox.inflate(8.0)).isNotEmpty()
    }
}

private fun target(minecraft: Minecraft) =
    minecraft.level!!.getEntitiesOfClass(Zombie::class.java, minecraft.player!!.boundingBox.inflate(30.0)).single()

private fun block(pos: BlockPos, block: String) = "block ${pos.x} ${pos.y} ${pos.z} $block"

private fun GameTestScope.health(entity: String) =
    query("data get entity $entity Health", Regex("""entity data: ([\d.]+)f""")).groupValues[1].toFloat()

private fun GameTestScope.awaitHealthBelow(entity: String, limit: Float, timeout: Int = 200): Float {
    repeat(timeout / 10) {
        val health = health(entity)
        if (health < limit) {
            return health
        }
        ticks(10)
    }
    throw AssertionError("$entity never went below $limit health, see ${server.log}")
}

private fun GameTestScope.lookAtTop(pos: BlockPos) = lookAt(Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5))

private fun GameTestScope.lookAt(point: Vec3) {
    val rotation = client { Rotation.lookingAt(point, it.player!!.eyePosition) }
    input.lookAt(rotation.yaw, rotation.pitch)
    ticks(2)
}
