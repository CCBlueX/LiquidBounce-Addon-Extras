package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
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
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBucketSteal
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrystalMacro
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.zombie.Husk
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
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

private fun GameTestScope.awaitSlot(slot: Int, timeout: Int = 200) =
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: $slot", timeout)

private fun GameTestScope.awaitScore(objective: String, score: Int) =
    awaitAnswer("scoreboard players get $playerName $objective", "has $score [$objective]")
