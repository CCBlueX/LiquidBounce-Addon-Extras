package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoBreed
import net.minecraft.world.entity.animal.Animal
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

class AutoBreedGameTest : PaperGameTest({
    run("scoreboard objectives add extras_age dummy")
    for ((species, food) in listOf(
        "cow" to "wheat", "mooshroom" to "wheat", "sheep" to "wheat", "pig" to "carrot",
        "chicken" to "wheat_seeds", "rabbit" to "carrot", "fox" to "sweet_berries",
        "bee" to "dandelion", "goat" to "wheat", "armadillo" to "spider_eye",
        "camel" to "cactus",
    )) {
        breedPair(species, food)
    }
})

class AutoBreedRareGameTest : PaperGameTest({
    run("scoreboard objectives add extras_age dummy")
    breedPair("sniffer", "torchflower_seeds")
    setBlock(origin.south(4), "minecraft:bamboo")
    breedPair("panda", "bamboo", extra = "MainGene:'normal',HiddenGene:'normal'")
    run("difficulty normal")
    breedPair("hoglin", "crimson_fungus", extra = "IsImmuneToZombification:1b")
    run("difficulty peaceful")
    breedPair("strider", "warped_fungus")
})

class AutoBreedTamedGameTest : PaperGameTest({
    run("scoreboard objectives add extras_age dummy")
    val uuid = client { net.minecraft.core.UUIDUtil.uuidToIntArray(it.player!!.uuid).joinToString(",", "[I;", "]") }
    for ((species, food) in listOf(
        "horse" to "golden_carrot", "donkey" to "golden_carrot",
        "llama" to "hay_block", "trader_llama" to "hay_block",
    )) {
        breedPair(species, food, extra = "Tame:1b,Owner:$uuid")
    }
    breedPair("wolf", "beef", extra = "Owner:$uuid,Sitting:0b")
    breedPair("cat", "cod", extra = "Owner:$uuid,Sitting:0b")
    breedPair("ocelot", "cod", extra = "Trusting:1b")
})

class AutoBreedAquaticGameTest : PaperGameTest({
    run("scoreboard objectives add extras_age dummy")
    fill(origin.offset(-2, -1, -2), origin.offset(3, 2, 5), "minecraft:stone hollow")
    fill(origin.offset(-2, 2, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-1, 0, -1), origin.offset(2, 1, 4), "minecraft:water")
    run("effect give $playerName minecraft:water_breathing 600 0 true")
    val uuid = client { net.minecraft.core.UUIDUtil.uuidToIntArray(it.player!!.uuid).joinToString(",", "[I;", "]") }
    breedPair("axolotl", "tropical_fish_bucket", aquatic = true)
    breedPair("nautilus", "cod", extra = "Owner:$uuid,Sitting:0b", aquatic = true)
    fill(origin.offset(-2, 0, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-2, -1, -2), origin.offset(3, -1, 5), "minecraft:sand")
    breedPair("turtle", "seagrass", extra = "home_pos:[I;0,-60,2]", eggs = true)
    fill(origin.offset(-2, 0, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-1, -1, 3), origin.offset(2, -1, 4), "minecraft:water")
    breedPair("frog", "slime_ball", extra = "Brain:{memories:{'minecraft:long_jump_cooling_down':{value:600}}}",
        eggs = true)
})

private fun GameTestScope.breedPair(
    species: String,
    food: String,
    extra: String = "",
    aquatic: Boolean = false,
    eggs: Boolean = false,
) {
    disable(ModuleAutoBreed)
    run("kill @e[type=!minecraft:player]")
    awaitClient("previous animals to disappear") {
        it.level!!.entitiesForRendering().none { entity -> entity is Animal }
    }
    travel(origin)
    run("clear $playerName")
    val count = if (food.endsWith("bucket")) 1 else 16
    run("item replace entity $playerName hotbar.3 with minecraft:$food $count")
    if (count == 1) run("item replace entity $playerName hotbar.4 with minecraft:$food")
    val details = "{PersistenceRequired:1b,Tags:['parents']${if (extra.isEmpty()) "" else ",$extra"}}"
    summon("minecraft:$species", origin.south(2), details)
    summon("minecraft:$species", origin.south(2).east(), details)
    run("execute as @e[tag=parents] run attribute @s minecraft:movement_speed base set 0")
    awaitClient("the $species pair and food") { minecraft ->
        BuiltInRegistries.ITEM.getKey(minecraft.player!!.inventory.getItem(3).item).path == food &&
            minecraft.level!!.entitiesForRendering()
            .count { it is Animal && !it.isBaby } == 2
    }
    enable(ModuleAutoBreed)
    awaitBreedConfirmation(species)
    if (species == "frog") run("execute as @e[tag=parents] run attribute @s minecraft:movement_speed base set 1")
    // Feeding alone is not reproduction. Brains and breeding goals run normally throughout the test.
    when {
        species == "sniffer" -> awaitServer("entity @e[type=minecraft:item,nbt={Item:{id:'minecraft:sniffer_egg'}}]")
        eggs -> awaitEggs(species)
        else -> {
            awaitClient("a baby $species (aquatic=$aquatic)", timeout = 400) { minecraft ->
                minecraft.level!!.entitiesForRendering().any {
                    it is Animal && it.isAlive && it.isBaby &&
                        BuiltInRegistries.ENTITY_TYPE.getKey(it.type).path == species
                }
            }
            run("execute as @e[type=minecraft:$species] store result score @s extras_age run data get entity @s Age")
            awaitServer("entity @e[scores={extras_age=..-1}]")
        }
    }
    if (species == "cow") {
        travel(origin.north(3), pitch = 15f)
        screenshot("AutoBreed")
    }
    ticks(40)
    disable(ModuleAutoBreed)
    check(client { ModuleAutoBreed.confirmed.isEmpty() })
}

private fun GameTestScope.awaitEggs(species: String) {
    val block = if (species == "turtle") Blocks.TURTLE_EGG else Blocks.FROGSPAWN
    val area = BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 2, 8))
    try {
        awaitClient("$species eggs laid", timeout = 1200) { minecraft ->
            area.any { minecraft.level!!.getBlockState(it).`is`(block) }
        }
    } catch (failure: AssertionError) {
        run("execute as @e[tag=parents] run data get entity @s")
        ticks(2)
        throw failure
    }
    val pos = client { minecraft -> area.first { minecraft.level!!.getBlockState(it).`is`(block) }.immutable() }
    awaitServer("block ${pos.x} ${pos.y} ${pos.z} ${BuiltInRegistries.BLOCK.getKey(block)}")
}

private fun GameTestScope.awaitBreedConfirmation(species: String) {
    try {
        awaitClient("server breeding confirmation for $species", timeout = 300) { ModuleAutoBreed.confirmed.size == 2 }
    } catch (failure: AssertionError) {
        run("execute as @e[tag=parents] run data get entity @s")
        screenshot("BreedingFailure-$species")
        val animals = client { minecraft ->
            minecraft.level!!.entitiesForRendering().filterIsInstance<Animal>().map {
                "${it.type}: pos=${it.position()}, health=${it.health}/${it.maxHealth}, baby=${it.isBaby}, " +
                    "tame=${(it as? TamableAnimal)?.isTame}, sitting=${(it as? TamableAnimal)?.isInSittingPose}"
            }
        }
        throw AssertionError("$species feeding failed: $animals", failure)
    }
}
