package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBucketSteal
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrystalMacro
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleMisplayGuard
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleTotemSwitch
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.zombie.Husk
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class CrystalMacroGameTest : PaperGameTest({
    run("difficulty normal")
    val key = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G)
    client {
        ModuleCrystalMacro.setting<InputConstants.Key>("Key").set(key)
        ModuleCrystalMacro.setting<Int>("LootPause").set(400)
    }
    // The blasts would dig a crater under the player
    fill(origin.offset(-5, -1, -3), origin.offset(5, -1, 8), "minecraft:bedrock")
    val stone = origin.offset(0, 0, 3)
    val obsidian = stone.above()
    setBlock(stone, "minecraft:stone")
    run("effect give $playerName minecraft:resistance infinite 4 true")
    run("item replace entity $playerName hotbar.3 with minecraft:obsidian 4")
    run("item replace entity $playerName hotbar.4 with minecraft:end_crystal 4")
    run("scoreboard objectives add crystals minecraft.used:minecraft.end_crystal")
    // Level with the crystal: from below, the obsidian would shield the husk from the blast
    fill(origin.offset(1, 0, 4), origin.offset(1, 1, 4), "minecraft:stone")
    summon("minecraft:husk", origin.offset(1, 2, 4), "{NoAI:1b,PersistenceRequired:1b}")
    awaitClient("the husk and the crystals") { minecraft ->
        minecraft.player!!.inventory.getItem(4).`is`(Items.END_CRYSTAL) && husks(minecraft.level!!) == 1
    }

    lookAt(Vec3(0.5, stone.y + 1.0, 3.5))
    enable(ModuleCrystalMacro)
    ticks(20)
    awaitServer("block ${obsidian.command} minecraft:air", 20)

    input.pressKey(key)
    awaitServer("block ${obsidian.command} minecraft:obsidian")
    awaitScore("crystals", 1)
    awaitAnswer("execute unless entity @e[type=minecraft:end_crystal]", "Test passed")
    awaitAnswer("execute unless entity @e[type=minecraft:husk]", "Test passed")

    // The husk died in the blast; what lies where it died is not blown up
    awaitClient("the dead husk to be noticed") { ModuleCrystalMacro.lootSite != null }
    val site = client { ModuleCrystalMacro.lootSite!! }
    val totem = "{Item:{id:\"minecraft:totem_of_undying\",count:1},PickupDelay:32767}"
    run("summon minecraft:item ${site.x} ${site.y} ${site.z} $totem")
    awaitClient("the totem on the ground") { items(it.level!!) > 0 }
    lookAt(Vec3(0.5, obsidian.y + 0.5, 3.5))
    input.pressKey(key)
    ticks(40)
    awaitScore("crystals", 1)

    run("kill @e[type=minecraft:item]")
    awaitClient("the totem to be gone") { items(it.level!!) == 0 }
    input.pressKey(key)
    awaitScore("crystals", 2)
    awaitClient("the loot pause to end") { ModuleCrystalMacro.lootSite == null }

    disable(ModuleCrystalMacro)
    check(client { !ModuleCrystalMacro.busy && ModuleCrystalMacro.lootSite == null }) { "state survived disabling" }
    lookAt(Vec3(0.5, obsidian.y + 0.5, 3.5))
    input.pressKey(key)
    ticks(40)
    awaitScore("crystals", 2)
})

class BucketStealGameTest : PaperGameTest({
    run("difficulty normal")
    val water = origin.offset(1, -1, 3)
    summon("minecraft:husk", origin.offset(0, 0, 3), "{NoAI:1b,PersistenceRequired:1b}")
    setBlock(water, "minecraft:water")
    run("item replace entity $playerName hotbar.2 with minecraft:bucket")
    client { ModuleBucketSteal.setting<Any>("RotationMode").setByString("None") }
    awaitClient("the husk, the pool and the bucket") { minecraft ->
        val level = minecraft.level!!
        minecraft.player!!.inventory.getItem(2).`is`(Items.BUCKET) && husks(level) == 1 &&
            level.getBlockState(water).`is`(Blocks.WATER)
    }

    // Without rotations, only what the crosshair is on
    enable(ModuleBucketSteal)
    ticks(30)
    awaitServer("block ${water.command} minecraft:water", 20)
    lookAt(Vec3(water.x + 0.5, water.y + 0.8, water.z + 0.5))
    awaitServer("block ${water.command} minecraft:air")
    awaitServer("entity @a[nbt={Inventory:[{id:\"minecraft:water_bucket\"}]}]")
    awaitSlot(0)

    // With them, the module aims on its own
    client { ModuleBucketSteal.setting<Any>("RotationMode").setByString("Normal") }
    input.lookAt(0f, 0f)
    val lava = origin.offset(-1, -1, 3)
    setBlock(lava, "minecraft:lava")
    run("item replace entity $playerName hotbar.5 with minecraft:bucket")
    awaitServer("block ${lava.command} minecraft:air")
    awaitServer("entity @a[nbt={Inventory:[{id:\"minecraft:lava_bucket\"}]}]")
    awaitSlot(0)

    // Out of empty buckets, a cobweb plugs the next source
    val pool = origin.offset(0, -1, 2)
    run("item replace entity $playerName hotbar.6 with minecraft:cobweb 4")
    setBlock(pool, "minecraft:water")
    awaitServer("block ${pool.command} minecraft:cobweb")
    awaitSlot(0)

    // OnWeb: only a source next to a cobweb
    client { ModuleBucketSteal.setting<Boolean>("OnWeb").set(true) }
    run("item replace entity $playerName hotbar.5 with minecraft:bucket 3")
    setBlock(water, "minecraft:water")
    ticks(30)
    awaitServer("block ${water.command} minecraft:water", 20)
    // Beside it in the ground, where it does not get in the way of the bucket
    setBlock(water.east(), "minecraft:cobweb")
    awaitServer("block ${water.command} minecraft:air")
    awaitSlot(0)

    // Delay: the second source waits out the pause after the first
    client {
        ModuleBucketSteal.setting<Boolean>("OnWeb").set(false)
        ModuleBucketSteal.setting<IntRange>("Delay").set(60..60)
    }
    ticks(10)
    setBlock(water, "minecraft:water")
    setBlock(lava, "minecraft:water")
    val sources = { level: Level -> listOf(water, lava).count { level.getBlockState(it).`is`(Blocks.WATER) } }
    awaitClient("the first source") { sources(it.level!!) == 1 }
    ticks(30)
    check(client { sources(it.level!!) } == 1) { "the second source did not wait for the delay" }
    awaitClient("the second source") { sources(it.level!!) == 0 }
    awaitSlot(0)

    disable(ModuleBucketSteal)
    run("item replace entity $playerName hotbar.5 with minecraft:bucket")
    setBlock(water, "minecraft:water")
    ticks(40)
    awaitServer("block ${water.command} minecraft:water", 20)
})

class MisplayGuardGameTest : PaperGameTest({
    run("difficulty normal")
    val anchor = origin.offset(0, 0, 2)
    val stone = origin.offset(-1, 0, 2)
    val chest = origin.offset(1, 0, 2)
    setBlock(anchor, "minecraft:respawn_anchor[charges=1]")
    setBlock(stone, "minecraft:stone")
    setBlock(chest, "minecraft:ender_chest")
    run("item replace entity $playerName hotbar.0 with minecraft:glowstone 8")
    run("item replace entity $playerName hotbar.1 with minecraft:end_crystal")
    run("item replace entity $playerName hotbar.2 with minecraft:firework_rocket 4")
    run("item replace entity $playerName hotbar.3 with minecraft:diamond_sword")
    for (objective in listOf("enderchest minecraft.custom:minecraft.open_enderchest",
        "rockets minecraft.used:minecraft.firework_rocket")) {
        run("scoreboard objectives add $objective")
        run("scoreboard players set $playerName ${objective.substringBefore(' ')} 0")
    }
    awaitClient("the blocks and the items") { minecraft ->
        minecraft.player!!.inventory.getItem(3).`is`(Items.DIAMOND_SWORD) &&
            minecraft.level!!.getBlockState(chest).`is`(Blocks.ENDER_CHEST)
    }
    enable(ModuleMisplayGuard)

    // A charged anchor gets no second charge
    aim(anchor)
    rightClick()
    ticks(20)
    awaitServer("block ${anchor.command} minecraft:respawn_anchor[charges=1]", 20)
    client { ModuleMisplayGuard.setting<Boolean>("ChargedAnchor").set(false) }
    rightClick()
    awaitServer("block ${anchor.command} minecraft:respawn_anchor[charges=2]")

    // Glowstone goes into anchors only
    aim(stone)
    val face = client { (it.hitResult as BlockHitResult).let { hit -> hit.blockPos.relative(hit.direction) } }
    rightClick()
    ticks(20)
    awaitServer("block ${face.command} minecraft:air", 20)
    client { ModuleMisplayGuard.setting<Boolean>("StrayGlowstone").set(false) }
    rightClick()
    awaitServer("block ${face.command} minecraft:glowstone")

    // The ender chest stays shut while a crystal is in hand
    input.pressKey { it.keyHotbarSlots[1] }
    aim(chest)
    rightClick()
    ticks(20)
    check(client { it.gui.screen() == null }) { "the ender chest opened" }
    awaitScore("enderchest", 0)
    client { ModuleMisplayGuard.setting<Boolean>("EnderChest").set(false) }
    rightClick()
    awaitClient("the ender chest to open") { it.gui.screen() is ContainerScreen }
    awaitScore("enderchest", 1)
    input.pressKey(InputConstants.KEY_ESCAPE)
    awaitClient("the ender chest to close") { it.gui.screen() == null }

    // No rockets from the ground, unless a crossbow in the offhand wants them
    input.pressKey { it.keyHotbarSlots[2] }
    aim(origin.offset(0, -1, 1))
    rightClick()
    ticks(20)
    awaitScore("rockets", 0)
    run("item replace entity $playerName weapon.offhand with minecraft:crossbow")
    awaitClient("the crossbow") { it.player!!.offhandItem.`is`(Items.CROSSBOW) }
    rightClick()
    awaitScore("rockets", 1)

    // Hits on anything without a raised shield still land
    val husk = origin.offset(1, 0, 1)
    summon("minecraft:husk", husk, "{NoAI:1b,PersistenceRequired:1b}")
    awaitClient("the husk") { husks(it.level!!) == 1 }
    input.pressKey { it.keyHotbarSlots[3] }
    aim(husk)
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitAnswer("execute unless entity @e[type=minecraft:husk,nbt={Health:20.0f}]", "Test passed")

    // Switched off, nothing is held back
    client { ModuleMisplayGuard.setting<Boolean>("ChargedAnchor").set(true) }
    disable(ModuleMisplayGuard)
    input.pressKey { it.keyHotbarSlots[0] }
    aim(anchor)
    rightClick()
    awaitServer("block ${anchor.command} minecraft:respawn_anchor[charges=3]")
})

class TotemSwitchGameTest : PaperGameTest({
    run("difficulty normal")
    run("gamerule minecraft:natural_health_regeneration false")
    run("item replace entity $playerName hotbar.0 with minecraft:stick")
    run("item replace entity $playerName hotbar.5 with minecraft:totem_of_undying")
    awaitClient("the totem") { it.player!!.inventory.getItem(5).`is`(Items.TOTEM_OF_UNDYING) }
    enable(ModuleTotemSwitch)

    // Your own ender pearl in the air
    client { ModuleTotemSwitch.setting<Boolean>("Pearl").set(true) }
    run("item replace entity $playerName hotbar.1 with minecraft:ender_pearl")
    awaitClient("the pearl") { it.player!!.inventory.getItem(1).`is`(Items.ENDER_PEARL) }
    input.pressKey { it.keyHotbarSlots[1] }
    input.lookAt(0f, -90f)
    ticks(2)
    rightClick()
    awaitSlot(5)
    awaitSlot(1)
    input.pressKey { it.keyHotbarSlots[0] }
    awaitSlot(0)
    // The landing hurt; the threats below expect full health
    run("effect give $playerName minecraft:instant_health 1 4 true")

    // A crystal two blocks away
    summon("minecraft:end_crystal", origin.south(2), "{ShowBottom:0b}")
    awaitSlot(5)
    run("kill @e[type=minecraft:end_crystal]")
    awaitSlot(0)

    // An enemy on its own is harmless; with obsidian in its reach it could crystal you
    summon("minecraft:husk", origin.south(4), "{NoAI:1b,PersistenceRequired:1b}")
    awaitClient("the husk") { husks(it.level!!) == 1 }
    ticks(30)
    awaitSlot(0)
    setBlock(origin.offset(0, -1, 2), "minecraft:obsidian")
    awaitSlot(5)
    run("kill @e[type=minecraft:husk]")
    awaitSlot(0)
    setBlock(origin.offset(0, -1, 2), "minecraft:grass_block")

    // An enemy coming down with a mace
    summon(
        "minecraft:husk", origin.offset(2, 12, 2),
        "{PersistenceRequired:1b,equipment:{mainhand:{id:\"minecraft:mace\",count:1}}}",
    )
    awaitSlot(5, timeout = 60)
    run("kill @e[type=minecraft:husk]")
    awaitSlot(0)

    // A sword only counts at low health
    summon(
        "minecraft:husk", origin.south(2),
        "{NoAI:1b,PersistenceRequired:1b,equipment:{mainhand:{id:\"minecraft:diamond_sword\",count:1}}}",
    )
    awaitClient("the husk") { husks(it.level!!) == 1 }
    ticks(30)
    awaitSlot(0)
    run("damage $playerName 14")
    awaitSlot(5)

    // Disabling gives the slot back
    disable(ModuleTotemSwitch)
    awaitSlot(0)
    check(client { ModuleTotemSwitch.previousSlot == null }) { "the previous slot survived disabling" }
    run("kill @e[type=minecraft:husk]")
})

private val AABB_AROUND = AABB(-16.0, -80.0, -16.0, 16.0, -40.0, 16.0)

private val BlockPos.command get() = "$x $y $z"

private fun items(level: Level) =
    level.getEntitiesOfClass(ItemEntity::class.java, AABB_AROUND) { it.item.`is`(Items.TOTEM_OF_UNDYING) }.size

private fun husks(level: Level) =
    level.getEntitiesOfClass(Husk::class.java, AABB_AROUND) { it.isAlive }.size

private fun GameTestScope.lookAt(point: Vec3) {
    val rotation = Rotation.lookingAt(point, client { it.player!!.eyePosition })
    input.lookAt(rotation.yaw, rotation.pitch)
    ticks(2)
}

private fun GameTestScope.rightClick() = input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)

private fun GameTestScope.awaitSlot(slot: Int, timeout: Int = 200) =
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: $slot", timeout)

private fun GameTestScope.awaitScore(objective: String, score: Int) =
    awaitAnswer("scoreboard players get $playerName $objective", "has $score [$objective]")
