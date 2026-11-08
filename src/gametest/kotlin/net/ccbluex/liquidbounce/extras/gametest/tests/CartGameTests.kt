package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCartBomb
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

private const val STAND_IN = "standin"
private const val FLAME_BOW = "minecraft:bow[enchantments={'minecraft:flame':1}]"
private const val INVENTORY_0 = 9

class CartBombGameTest : PaperGameTest({
    arena()
    val module = ModuleCartBomb
    val safeBlock = client { module.containedValues.first { it.name == "SafeBlock" } as ValueGroup }
    client {
        module.setting<InputConstants.Key>("Key").set(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J))
        module.setting<Int>("Carts").set(2)
        safeBlock.setting<Boolean>("Enabled").set(true)
    }
    enable(module)

    // Two carts on a new rail, a block at our feet, then a Flame bow; the bow ends up in hand
    val rail = origin.south(4)
    kit(FLAME_BOW)
    run("item replace entity $playerName hotbar.4 with minecraft:obsidian 4")
    standIn(rail.south(2))
    lookAt(Vec3(0.5, rail.y.toDouble(), rail.z + 0.5))
    input.pressKey(InputConstants.KEY_J)
    awaitAnswer("execute if entity @e[type=minecraft:tnt_minecart]", "Count: 2", 100)
    awaitStandInHurt()
    awaitServer("block 0 -60 1 minecraft:obsidian")
    awaitCarts(1)
    awaitClient("the bow in hand") { it.player!!.mainHandItem.`is`(Items.BOW) }

    // A loaded crossbow fired through fire lit on the arrow's path
    client {
        module.setting<Int>("Carts").set(1)
        safeBlock.setting<Boolean>("Enabled").set(false)
    }
    setBlock(origin.south(), "minecraft:air")
    travel(origin)
    kit("minecraft:crossbow[charged_projectiles=[{id:'minecraft:arrow',count:1}]]")
    run("item replace entity $playerName hotbar.5 with minecraft:flint_and_steel")
    standIn(rail.south(2))
    lookAt(Vec3(0.5, rail.y.toDouble(), rail.z + 0.5))
    input.pressKey(InputConstants.KEY_J)
    awaitStandInHurt()
    awaitServer("items entity $playerName container.* minecraft:flint_and_steel[damage=1]")
    awaitCarts(2)

    // A short, steep shot by hand: rail and cart go where it comes down, before it does
    fill(origin.offset(-2, 0, 1), origin.offset(2, 1, 8), "minecraft:air")
    travel(origin)
    kit(FLAME_BOW)
    input.pressKey { it.keyHotbarSlots[3] }
    standIn(origin.south(6))
    input.lookAt(0f, -80f)
    input.holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 6)
    awaitClient("the predicted landing") { module.shotRail != null }
    val landing = client { module.shotRail!! }
    check(landing.y == origin.y && landing.z in 2..5) { "the shot was predicted to land at $landing" }
    awaitStandInHurt()
    awaitServer("items entity $playerName hotbar.1 minecraft:rail[count=15]")
    awaitCarts(2)

    // A cart someone left next to the enemy: drawing the bow while looking away is enough to set it off
    client {
        module.setting<Boolean>("OnShot").set(false)
        val hitPlaced = module.containedValues.first { it.name == "HitPlaced" } as ValueGroup
        hitPlaced.setting<Boolean>("Enabled").set(true)
    }
    fill(origin.offset(-2, 0, 1), origin.offset(2, 1, 8), "minecraft:air")
    travel(origin)
    kit(FLAME_BOW)
    val placed = origin.south(4)
    setBlock(placed, "minecraft:rail")
    summon("minecraft:tnt_minecart", placed)
    standIn(placed.south())
    awaitClient("the placed cart") { minecraft ->
        minecraft.level!!.entitiesForRendering().any { it is MinecartTNT }
    }
    input.pressKey { it.keyHotbarSlots[3] }
    input.lookAt(90f, 0f)
    input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitStandInHurt()
    input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitAnswer("execute unless entity @e[type=minecraft:tnt_minecart]", "Test passed", 100)
    awaitCarts(3)

    // Overload stacks four carts, the last pulled up from the inventory, and shoots them
    val overload = client { module.containedValues.first { it.name == "Overload" } as ValueGroup }
    client {
        overload.setting<InputConstants.Key>("Key").set(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_K))
        overload.setting<Int>("Carts").set(4)
        overload.setting<Boolean>("HotbarOnly").set(false)
    }
    fill(origin.offset(-2, 0, 1), origin.offset(2, 1, 8), "minecraft:air")
    run("item replace entity $playerName inventory.1 with minecraft:tnt_minecart")
    awaitClient("the spare cart") { it.player!!.inventory.getItem(INVENTORY_0 + 1).`is`(Items.TNT_MINECART) }
    // The last blast pushed us back
    travel(origin)
    standIn(rail.south(2))
    lookAt(Vec3(0.5, rail.y.toDouble(), rail.z + 0.5))
    input.pressKey(InputConstants.KEY_K)
    awaitAnswer("execute if entity @e[type=minecraft:tnt_minecart]", "Count: 4", 200)
    awaitStandInHurt()
    disable(module)
    check(client { module.shotRail == null }) { "the predicted landing survived disabling" }
})

/** Monsters need more than peaceful; resistance keeps us out of our own blasts, obsidian keeps the floor. */
private fun GameTestScope.arena() {
    run("difficulty normal")
    run("effect give $playerName minecraft:resistance infinite 4 true")
    fill(origin.offset(-8, -1, -8), origin.offset(8, -1, 12), "minecraft:obsidian")
}

/** The [launcher], rails, three TNT minecarts and arrows, nothing in hand. */
private fun GameTestScope.kit(launcher: String) {
    run("clear $playerName")
    // Otherwise the last kit's leftovers would pass for this one
    awaitClient("the emptied inventory") { it.player!!.inventory.isEmpty }
    run("item replace entity $playerName hotbar.1 with minecraft:rail 16")
    // They do not stack
    for (slot in listOf(2, 6, 7)) {
        run("item replace entity $playerName hotbar.$slot with minecraft:tnt_minecart")
    }
    run("item replace entity $playerName hotbar.3 with $launcher")
    run("item replace entity $playerName inventory.0 with minecraft:arrow 16")
    // Grim ends the server's item use when the item in hand changes, a draw begun before the kit arrived too
    awaitClient("the kit") { minecraft ->
        listOf(1, 2, 3, 6, 7, INVENTORY_0).none { minecraft.player!!.inventory.getItem(it).isEmpty }
    }
}

/** TNT minecarts left in the inventory, as the server counts them. */
private fun GameTestScope.awaitCarts(count: Int) =
    awaitAnswer("clear $playerName minecraft:tnt_minecart 0", "Found $count matching")

/** An enemy that cannot die of our blasts: armoured against the sun, with a thousand health. */
private fun standInNbt(extra: String = "") =
    "{NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:['$STAND_IN'],Health:1000f" +
        ",attributes:[{id:'minecraft:max_health',base:1000d}]" +
        ",equipment:{head:{id:'minecraft:iron_helmet',count:1}}$extra}"

private fun GameTestScope.standIn(pos: BlockPos) {
    // The last one stands on the same spot until the client learns that it died
    val before = client { minecraft ->
        minecraft.level!!.entitiesForRendering().filterIsInstance<Zombie>().map { it.id }
    }
    // A dying stand-in lingers for a second and would count as hurt
    run("tag @e[tag=$STAND_IN] remove $STAND_IN")
    run("kill @e[type=minecraft:zombie]")
    summon("minecraft:zombie", pos, standInNbt())
    awaitClient("the stand-in") { minecraft ->
        minecraft.level!!.entitiesForRendering().any { it is Zombie && it.id !in before }
    }
}

private fun GameTestScope.awaitStandInHurt() = awaitServer("entity @e[tag=$STAND_IN,nbt=!{Health:1000.0f}]")

private fun GameTestScope.lookAt(point: Vec3) {
    val rotation = client { Rotation.lookingAt(point, it.player!!.eyePosition) }
    input.lookAt(rotation.yaw, rotation.pitch)
    ticks(2)
}
