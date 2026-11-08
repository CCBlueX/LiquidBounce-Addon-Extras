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
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.hud.CartKitHud
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCartBomb
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCartBreaker
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCartCover
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlCart
import net.ccbluex.liquidbounce.extras.modules.pvp.OwnCarts
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

private const val STAND_IN = "standin"
private const val FLAME_BOW = "minecraft:bow[enchantments={'minecraft:flame':1}]"
private const val INVENTORY_0 = 9
private val ARMOR = mapOf("head" to "helmet", "chest" to "chestplate", "legs" to "leggings", "feet" to "boots")

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

class PearlCartGameTest : PaperGameTest({
    arena()
    kit(FLAME_BOW)
    // Far enough that a blast at the landing spot cannot reach it there
    summon("minecraft:zombie", origin.south(40), standInNbt(",UUID:[I;1,2,3,4]"))
    awaitClient("the thrower") { minecraft -> minecraft.level!!.entitiesForRendering().any { it is Zombie } }
    enable(ModulePearlCart)

    // A blast that netherite keeps below 40 for it, then one that would hurt us at all: no cart for either
    for (piece in listOf("head", "chest", "legs", "feet")) {
        run("item replace entity @e[tag=$STAND_IN] armor.$piece with minecraft:netherite_${ARMOR[piece]}")
    }
    run("effect clear $playerName minecraft:resistance")
    for ((name, value) in listOf("MinEnemyDamage" to 40f, "MaxSelfDamage" to 0f)) {
        client {
            ModulePearlCart.setting<Float>("MinEnemyDamage").set(0f)
            ModulePearlCart.setting<Float>("MaxSelfDamage").set(40f)
            ModulePearlCart.setting<Float>(name).set(value)
        }
        // The effect packets first
        ticks(5)
        summon("minecraft:ender_pearl", origin.south(4).above(10), "{Owner:[I;1,2,3,4],Motion:[0.0d,0.2d,0.0d]}")
        awaitClient("the pearl") { minecraft ->
            minecraft.level!!.entitiesForRendering().any { it is ThrownEnderpearl }
        }
        awaitAnswer("execute unless entity @e[type=minecraft:ender_pearl]", "Test passed", 100)
        ticks(20)
        awaitCarts(3)
        run("tp @e[tag=$STAND_IN] 0 -60 40")
        run("data merge entity @e[tag=$STAND_IN,limit=1] {Health:1000f}")
    }
    run("effect give $playerName minecraft:resistance infinite 4 true")
    for (piece in listOf("chest", "legs", "feet")) {
        run("item replace entity @e[tag=$STAND_IN] armor.$piece with minecraft:air")
    }
    run("item replace entity @e[tag=$STAND_IN] armor.head with minecraft:iron_helmet")
    client {
        ModulePearlCart.setting<Float>("MinEnemyDamage").set(0f)
        ModulePearlCart.setting<Float>("MaxSelfDamage").set(40f)
    }

    summon("minecraft:ender_pearl", origin.south(4).above(10), "{Owner:[I;1,2,3,4],Motion:[0.0d,0.2d,0.0d]}")
    // Only a blast at the landing spot reaches it, and it keeps the place the pearl took it to
    awaitStandInHurt()
    awaitServer("entity @e[tag=$STAND_IN,x=0,y=-60,z=0,distance=..20]")
    awaitCarts(2)
    disable(ModulePearlCart)
    check(client { ModulePearlCart.pearls.isEmpty() }) { "met pearls survived disabling" }
})

class CartBreakerGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.0 with minecraft:tnt_minecart")
    run("item replace entity $playerName hotbar.4 with minecraft:iron_sword")
    val own = origin.south(2)
    setBlock(own, "minecraft:rail")
    awaitClient("the rail and the cart") { minecraft ->
        minecraft.level!!.getBlockState(own).`is`(net.minecraft.world.level.block.Blocks.RAIL) &&
            minecraft.player!!.inventory.getItem(0).`is`(Items.TNT_MINECART)
    }
    lookAt(Vec3(0.5, own.y + 0.05, own.z + 0.5))
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer("entity @e[type=minecraft:tnt_minecart]")
    awaitClient("the cart to count as ours") { OwnCarts.ids.size == 1 }

    enable(ModuleCartBreaker)
    ticks(60)
    awaitServer("entity @e[type=minecraft:tnt_minecart]")

    // Someone else's behind us is out of view, the one ahead is not
    summon("minecraft:tnt_minecart", origin.north(2))
    ticks(60)
    awaitAnswer("execute if entity @e[type=minecraft:tnt_minecart]", "Count: 2")
    summon("minecraft:tnt_minecart", origin.south(2).east(2))
    awaitClient("the breaker to pick the cart") { ModuleCartBreaker.target != null }
    awaitServer("entity @e[type=minecraft:item,nbt={Item:{id:'minecraft:tnt_minecart'}}]")
    awaitAnswer("execute if entity @e[type=minecraft:tnt_minecart]", "Count: 2")
    disable(ModuleCartBreaker)
    check(client { ModuleCartBreaker.target == null }) { "the target survived disabling" }
})

class CartCoverGameTest : PaperGameTest({
    run("difficulty normal")
    fill(origin.offset(-6, -1, -6), origin.offset(6, -1, 8), "minecraft:obsidian")
    run("item replace entity $playerName hotbar.2 with minecraft:cobblestone 16")
    val rail = origin.south(4)
    setBlock(rail, "minecraft:activator_rail")
    summon("minecraft:tnt_minecart", rail)
    awaitClient("the cart") { minecraft -> minecraft.level!!.entitiesForRendering().any { it is MinecartTNT } }
    enable(ModuleCartCover)

    // Unlit, so nothing to fear
    ticks(40)
    awaitServer("items entity $playerName hotbar.2 minecraft:cobblestone[count=16]")

    // Powering the activator rail lights it; four blocks away its blast would kill. Someone else's cart is
    // nothing to cover against while only our own count
    client {
        ModuleCartCover.setting<Any>("Protect").setByString("Own")
        ModuleCartCover.setting<Int>("MaxBlocks").set(1)
    }
    setBlock(rail.below(), "minecraft:redstone_block")
    awaitClient("the lit cart") { minecraft ->
        minecraft.level!!.entitiesForRendering().any { it is MinecartTNT && it.isPrimed }
    }
    ticks(10)
    awaitServer("items entity $playerName hotbar.2 minecraft:cobblestone[count=16]")
    // Then one block against it, no more
    client { ModuleCartCover.setting<Any>("Protect").setByString("Both") }
    awaitServer("items entity $playerName hotbar.2 minecraft:cobblestone[count=15]")
    ticks(10)
    awaitServer("items entity $playerName hotbar.2 minecraft:cobblestone[count=15]")
    client { ModuleCartCover.setting<Int>("MaxBlocks").set(26) }
    awaitClient("the covered player") { ModuleCartCover.predictedDamage in 0.1f..4f }
    input.lookAt(0f, 35f)
    ticks(2)
    screenshot("CartCover")
    awaitAnswer("execute unless entity @e[type=minecraft:tnt_minecart]", "Test passed", 200)
    ticks(20)
    check(server.console.none { playerName in it.message && "blown up" in it.message }) { "the blast killed us" }
    val health = query("data get entity $playerName Health", Regex("""([\d.]+)f""")).groupValues[1].toFloat()
    check(health >= 15f) { "the blast took us down to $health" }
    disable(ModuleCartCover)
    check(client { ModuleCartCover.predictedDamage == 0f }) { "the prediction survived disabling" }
})

class CartKitHudGameTest : PaperGameTest({
    kit(FLAME_BOW)
    client { CartKitHud.enabled = true }
    val text = client { CartKitHud.text.string }
    check("Rails 16" in text && "Carts 3" in text && "Bow" in text) { "the kit reads '$text'" }
    input.pressKey { it.keyHotbarSlots[3] }
    input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    ticks(12)
    screenshot("CartKit")
    input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    client { CartKitHud.enabled = false }
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
