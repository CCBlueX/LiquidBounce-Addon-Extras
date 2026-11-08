package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBlastCover
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.platform.InputConstants

private const val PLAYER_HEALTH = 20f

class BlastCoverGameTest : PaperGameTest({
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

private fun block(pos: BlockPos, block: String) = "block ${pos.x} ${pos.y} ${pos.z} $block"

private fun GameTestScope.health(entity: String) =
    query("data get entity $entity Health", Regex("""entity data: ([\d.]+)f""")).groupValues[1].toFloat()

private fun GameTestScope.lookAtTop(pos: BlockPos) = lookAt(Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5))

private fun GameTestScope.lookAt(point: Vec3) {
    val rotation = client { Rotation.lookingAt(point, it.player!!.eyePosition) }
    input.lookAt(rotation.yaw, rotation.pitch)
    ticks(2)
}
