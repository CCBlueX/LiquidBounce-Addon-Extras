package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.give
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoAnvilRepair
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoBreed
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoShearer
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSign
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSmelter
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
import com.mojang.blaze3d.platform.InputConstants

class AutoJumpGameTest : PaperGameTest({
    enable(ModuleAutoJump.INSTANCE)
    input.holdKey { it.keyUp }
    awaitClient("a jump", timeout = 40) { !it.player!!.onGround() }
    ticks(10)
    screenshot("AutoJump")
    ticks(60)
    input.releaseKey { it.keyUp }
})

class AutoShearerGameTest : PaperGameTest({
    // Off the selected slot, so the module has to switch to them
    run("item replace entity $playerName hotbar.3 with minecraft:shears")
    summon("minecraft:sheep", origin.south(2), "{NoAI:1b}")
    awaitClient("the sheep and the shears") { minecraft ->
        val player = minecraft.player!!
        player.inventory.getItem(3).`is`(Items.SHEARS) &&
            minecraft.level!!.getEntitiesOfClass(Sheep::class.java, player.boundingBox.inflate(4.0)).isNotEmpty()
    }

    enable(ModuleAutoShearer)
    awaitServer("entity @e[type=minecraft:sheep,nbt={Sheared:1b}]")
    ticks(10)
    screenshot("AutoShearer")
})

class AutoSignGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.0 with minecraft:oak_sign 2")
    awaitClient("the signs") { it.player!!.mainHandItem.`is`(Items.OAK_SIGN) }
    enable(ModuleAutoSign.INSTANCE)

    // Nothing written yet, so the first sign is written by hand
    val first = origin.south(2)
    aim(first.below())
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    context.waitForScreen(AbstractSignEditScreen::class.java)
    input.typeChars("Extras")
    context.clickScreenButton("gui.done")
    awaitSignText(first, "Extras")

    val second = origin.south(2).east()
    aim(second.below())
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    repeat(20) {
        check(client { it.gui.screen() !is AbstractSignEditScreen }) { "the sign editor opened" }
        ticks(1)
    }
    awaitSignText(second, "Extras")
    screenshot("AutoSign")
})

class AutoSignTemplatesGameTest : PaperGameTest({
    val module = ModuleAutoSign.INSTANCE
    val nearby = client { module.containedValues.first { it.name == "Nearby" } as ValueGroup }
    client {
        module.setting<Any>("TextMode").setByString("Template")
        for (line in 1..4) {
            module.setting<String>("Front$line").set("Front $line")
            module.setting<String>("Back$line").set("Back $line")
        }
        nearby.setting<Boolean>("Enabled").set(true)
    }
    val sign = origin.south(2)

    placeSign(sign, "minecraft:oak_sign[rotation=8]")
    enable(module)
    for (line in 0..3) {
        awaitSignText(sign, "Front ${line + 1}", line = line)
    }
    disable(module)
    travel(origin.south(4), yaw = 180f)
    enable(module)
    for (line in 0..3) {
        awaitSignText(sign, "Back ${line + 1}", side = "back", line = line)
    }
    screenshot("AutoSign-templates")
    disable(module)

    travel(origin)
    placeSign(sign, "minecraft:oak_sign[rotation=8]{front_text:{messages:['Keep','','','']}}") {
        it.getText(SignTextSlot.FRONT).hasMessage(false)
    }
    enable(module)
    ticks(60)
    awaitSignText(sign, "Keep")
    client { nearby.setting<Boolean>("Overwrite").set(true) }
    awaitSignText(sign, "Front 1")
    disable(module)

    placeSign(sign, "minecraft:oak_sign[rotation=8]{is_waxed:1b}") { it.isWaxed }
    enable(module)
    ticks(60)
    awaitSignText(sign, "\"\"")
    disable(module)

    placeSign(sign, "minecraft:oak_sign[rotation=8]")
    run("gamemode adventure $playerName")
    awaitClient("adventure mode") { !it.player!!.mayBuild() }
    enable(module)
    ticks(60)
    awaitSignText(sign, "\"\"")
    disable(module)
    run("gamemode survival $playerName")
    awaitClient("survival mode") { it.player!!.mayBuild() }

    setBlock(sign.south(), "minecraft:stone")
    setBlock(sign.above(), "minecraft:stone")
    for (block in listOf("minecraft:oak_wall_sign[facing=north]", "minecraft:oak_hanging_sign[rotation=8]")) {
        placeSign(sign, block)
        enable(module)
        awaitSignText(sign, "Front 1")
        disable(module)
    }
})

class AutoAnvilRepairGameTest : PaperGameTest({
    val anvil = origin.south(2)
    setBlock(anvil, "minecraft:anvil")
    give("minecraft:diamond_pickaxe[damage=1000]", 2)
    run("experience set $playerName 30 levels")

    enable(ModuleAutoAnvilRepair)
    openAnvil(anvil)
    awaitServer("entity @a[name=$playerName,level=28]")
    check(pickaxes() == 1) { "the pair was not combined into one pickaxe" }
    screenshot("AutoAnvilRepair")
    client { it.player!!.closeContainer() }

    // Priced above MaxLevelCost: goes back untouched
    run("clear $playerName")
    give("minecraft:diamond_pickaxe[damage=1000,repair_cost=10]", 2)
    ticks(20)
    openAnvil(anvil)
    ticks(60)
    val inputs = client { minecraft ->
        val menu = (minecraft.gui.screen() as AnvilScreen).menu
        listOf(AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT).count { menu.getSlot(it).hasItem() }
    }
    check(inputs == 0) { "the expensive pair stayed in the anvil" }
    check(pickaxes() == 2) { "the expensive pair was combined" }
    awaitServer("entity @a[name=$playerName,level=28]")
    client { it.player!!.closeContainer() }
})

class AutoSmelterGameTest : PaperGameTest({
    val furnace = origin.south(2)
    val inputs = linkedSetOf(Items.RAW_IRON, Items.RAW_GOLD, Items.BEEF)
    client { ModuleAutoSmelter.setting<Set<Item>>("Inputs").set(inputs) }
    for ((station, ingredient, result) in listOf(
        Triple("furnace", "raw_iron", "iron_ingot"),
        Triple("blast_furnace", "raw_gold", "gold_ingot"),
        Triple("smoker", "beef", "cooked_beef"),
    )) {
        run("clear $playerName")
        setBlock(furnace, "minecraft:$station")
        give("minecraft:$ingredient", 2)
        give("minecraft:coal", 8)
        openFurnace(furnace)
        enable(ModuleAutoSmelter)
        awaitServer("items entity $playerName container.* minecraft:$result[count=2]", timeout = 600)
        awaitServer("items entity $playerName container.* minecraft:coal[count=7]")
        if (station == "furnace") {
            screenshot("AutoSmelter")
        }
        disable(ModuleAutoSmelter)
        client { it.player!!.closeContainer() }
    }

    // A smoker has no recipe for raw iron
    run("clear $playerName")
    give("minecraft:raw_iron", 8)
    give("minecraft:coal", 8)
    openFurnace(furnace)
    enable(ModuleAutoSmelter)
    ticks(60)
    awaitServer("items entity $playerName container.* minecraft:raw_iron[count=8]")
    disable(ModuleAutoSmelter)
    client { it.player!!.closeContainer() }

    client { ModuleAutoSmelter.setting<Int>("Refill").set(3) }
    setBlock(furnace, "minecraft:furnace")
    openFurnace(furnace)
    enable(ModuleAutoSmelter)
    awaitServer("items block ${furnace.x} ${furnace.y} ${furnace.z} container.1 minecraft:coal[count=2]")
    awaitServer("items entity $playerName container.* minecraft:coal[count=5]")
    disable(ModuleAutoSmelter)
    client { it.player!!.closeContainer() }

    // Closed halfway through a refill, the coal on the cursor goes back
    run("clear $playerName")
    give("minecraft:raw_iron")
    give("minecraft:coal", 8)
    client {
        val constraints = ModuleAutoSmelter.containedValues.first { it.name == "Constraints" } as ValueGroup
        constraints.setting<IntRange>("ClickDelay").set(20..20)
    }
    setBlock(furnace, "minecraft:air")
    setBlock(furnace, "minecraft:furnace")
    openFurnace(furnace)
    enable(ModuleAutoSmelter)
    awaitClient("coal on the cursor") { !it.player!!.containerMenu.carried.isEmpty }
    client { it.player!!.closeContainer() }
    ticks(30)
    awaitServer("items entity $playerName container.* minecraft:coal[count=8]")
    disable(ModuleAutoSmelter)

    run("clear $playerName")
    give("minecraft:raw_iron")
    give("minecraft:lava_bucket")
    client { ModuleAutoSmelter.setting<Set<Item>>("Fuels").set(linkedSetOf(Items.LAVA_BUCKET)) }
    setBlock(furnace, "minecraft:air")
    setBlock(furnace, "minecraft:furnace")
    openFurnace(furnace)
    enable(ModuleAutoSmelter)
    awaitServer("items entity $playerName container.* minecraft:bucket")
    awaitServer("items entity $playerName container.* minecraft:iron_ingot", timeout = 300)
    disable(ModuleAutoSmelter)
    client { it.player!!.closeContainer() }

    // Output that does not fit stays in the furnace
    setBlock(furnace, "minecraft:air")
    setBlock(furnace, "minecraft:furnace{Items:[{Slot:2b,id:'minecraft:iron_ingot',count:64}]}")
    repeat(36) { run("item replace entity $playerName container.$it with minecraft:cobblestone 64") }
    awaitClient("a full inventory") { it.player!!.inventory.nonEquipmentItems.all { stack -> stack.count == 64 } }
    openFurnace(furnace)
    enable(ModuleAutoSmelter)
    ticks(60)
    awaitServer("items block ${furnace.x} ${furnace.y} ${furnace.z} container.2 minecraft:iron_ingot[count=64]")
    disable(ModuleAutoSmelter)
    client { it.player!!.closeContainer() }
})

class AutoBreedGameTest : PaperGameTest({
    breed("cow", "wheat") {
        awaitBaby("cow")
        travel(origin.north(3), pitch = 15f)
        screenshot("AutoBreed")
    }
    for ((species, food) in listOf(
        "mooshroom" to "wheat", "sheep" to "wheat", "pig" to "carrot", "chicken" to "wheat_seeds",
        "rabbit" to "carrot", "fox" to "sweet_berries", "bee" to "dandelion", "goat" to "wheat",
        "armadillo" to "spider_eye", "camel" to "cactus",
    )) {
        breed(species, food)
    }
})

class AutoBreedRareGameTest : PaperGameTest({
    breed("sniffer", "torchflower_seeds") {
        awaitServer("entity @e[type=minecraft:item,nbt={Item:{id:'minecraft:sniffer_egg'}}]", timeout = 400)
    }
    setBlock(origin.south(4), "minecraft:bamboo")
    breed("panda", "bamboo", "MainGene:'normal',HiddenGene:'normal'")
    run("difficulty normal")
    breed("hoglin", "crimson_fungus", "IsImmuneToZombification:1b")
    run("difficulty peaceful")
    breed("strider", "warped_fungus")
})

class AutoBreedTamedGameTest : PaperGameTest({
    val owner = owner()
    for ((species, food) in listOf(
        "horse" to "golden_carrot", "donkey" to "golden_carrot", "llama" to "hay_block", "trader_llama" to "hay_block",
    )) {
        breed(species, food, "Tame:1b,Owner:$owner")
    }
    breed("wolf", "beef", "Owner:$owner,Sitting:0b")
    breed("cat", "cod", "Owner:$owner,Sitting:0b")
    breed("ocelot", "cod", "Trusting:1b")
})

class AutoBreedAquaticGameTest : PaperGameTest({
    fill(origin.offset(-2, -1, -2), origin.offset(3, 2, 5), "minecraft:stone hollow")
    fill(origin.offset(-2, 2, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-1, 0, -1), origin.offset(2, 1, 4), "minecraft:water")
    run("effect give $playerName minecraft:water_breathing 600 0 true")
    breed("axolotl", "tropical_fish_bucket")
    breed("nautilus", "cod", "Owner:${owner()},Sitting:0b")

    fill(origin.offset(-2, 0, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-2, -1, -2), origin.offset(3, -1, 5), "minecraft:sand")
    breed("turtle", "seagrass", "home_pos:[I;0,-60,2]") { awaitLaid(Blocks.TURTLE_EGG) }

    fill(origin.offset(-2, 0, -2), origin.offset(3, 2, 5), "minecraft:air")
    fill(origin.offset(-1, -1, 3), origin.offset(2, -1, 4), "minecraft:water")
    val restless = "Brain:{memories:{'minecraft:long_jump_cooling_down':{value:600}}}"
    breed("frog", "slime_ball", restless) {
        // Frogs have to swim to lay their spawn
        run("execute as @e[tag=parents] run attribute @s minecraft:movement_speed base set 1")
        awaitLaid(Blocks.FROGSPAWN)
    }
})

class AutoBreedSafetyGameTest : PaperGameTest({
    run("item replace entity $playerName weapon.offhand with minecraft:wheat 16")
    for (pos in listOf(origin.south(2), origin.south(2).east())) {
        summon("minecraft:cow", pos, "{NoAI:1b,Age:-24000}")
    }
    awaitClient("two calves") { it.level!!.entitiesForRendering().count { cow -> cow is Animal && cow.isBaby } == 2 }
    enable(ModuleAutoBreed)
    ticks(60)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=16]")

    run("execute as @e[type=minecraft:cow] run data merge entity @s {Age:0}")
    fill(origin.offset(-1, 0, 1), origin.offset(2, 2, 1), "minecraft:stone")
    ticks(60)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=16]")
    fill(origin.offset(-1, 0, 1), origin.offset(2, 2, 1), "minecraft:air")
    awaitClient("hearts for both cows") { ModuleAutoBreed.confirmed.size == 2 }
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=14]")
    ticks(140)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=14]")
    disable(ModuleAutoBreed)
    check(client { ModuleAutoBreed.confirmed.isEmpty() }) { "confirmations survived disabling" }
    // Their beef would end up in the offhand
    run("kill @e[type=minecraft:cow]")
    run("kill @e[type=minecraft:item]")

    // Wild, sitting and hurt wolves
    val owner = owner()
    for (wolf in listOf("{NoAI:1b}", "{NoAI:1b,Owner:$owner,Sitting:1b}", "{NoAI:1b,Owner:$owner,Health:2f}")) {
        run("kill @e[type=minecraft:wolf]")
        run("item replace entity $playerName weapon.offhand with minecraft:beef 16")
        summon("minecraft:wolf", origin.south(2), wolf)
        summon("minecraft:wolf", origin.south(2).east(), wolf)
        awaitClient("two wolves") { it.level!!.entitiesForRendering().count { entity -> entity is Wolf } == 2 }
        enable(ModuleAutoBreed)
        ticks(60)
        awaitServer("items entity $playerName weapon.offhand minecraft:beef[count=16]")
        disable(ModuleAutoBreed)
    }
})

private fun GameTestScope.awaitSignText(pos: BlockPos, text: String, side: String = "front", line: Int = 0) =
    awaitAnswer("data get block ${pos.x} ${pos.y} ${pos.z} ${side}_text.messages[$line]", text)

private fun GameTestScope.placeSign(pos: BlockPos, block: String, arrived: (SignBlockEntity) -> Boolean = { true }) {
    setBlock(pos, "minecraft:air")
    setBlock(pos, block)
    awaitClient("the sign") { (it.level!!.getBlockEntity(pos) as? SignBlockEntity)?.let(arrived) == true }
}

private fun GameTestScope.openFurnace(pos: BlockPos) {
    awaitClient("the furnace to arrive") { it.level!!.getBlockState(pos).block is AbstractFurnaceBlock }
    aim(pos)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the furnace to open") { it.gui.screen() is AbstractFurnaceScreen<*> }
}

/** Has a fresh pair of [species] in front of the player fed, and waits for what they [made], a baby by default. */
private fun GameTestScope.breed(
    species: String,
    food: String,
    nbt: String = "",
    made: GameTestScope.() -> Unit = { awaitBaby(species) },
) {
    run("kill @e[type=!minecraft:player]")
    awaitClient("the previous animals to go") { it.level!!.entitiesForRendering().none { entity -> entity is Animal } }
    travel(origin)
    run("clear $playerName")
    if (food.endsWith("_bucket")) {
        // Buckets do not stack, and each parent needs its own
        run("item replace entity $playerName hotbar.3 with minecraft:$food")
        run("item replace entity $playerName hotbar.4 with minecraft:$food")
    } else {
        run("item replace entity $playerName hotbar.3 with minecraft:$food 16")
    }
    val parent = "{PersistenceRequired:1b,Tags:['parents']${if (nbt.isEmpty()) "" else ",$nbt"}}"
    summon("minecraft:$species", origin.south(2), parent)
    summon("minecraft:$species", origin.south(2).east(), parent)
    // Standing still keeps them in reach until both are fed
    run("execute as @e[tag=parents] run attribute @s minecraft:movement_speed base set 0")
    awaitClient("the $species pair and its food") { minecraft ->
        !minecraft.player!!.inventory.getItem(3).isEmpty &&
            minecraft.level!!.entitiesForRendering().count { it is Animal && !it.isBaby } == 2
    }
    enable(ModuleAutoBreed)
    awaitClient("hearts for both") { ModuleAutoBreed.confirmed.size == 2 }
    made()
    disable(ModuleAutoBreed)
}

/** Both parents and their baby, as the server counts them. */
private fun GameTestScope.awaitBaby(species: String) =
    awaitAnswer("execute if entity @e[type=minecraft:$species]", "Count: 3", 400)

private fun GameTestScope.awaitLaid(block: Block) {
    val area = BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 2, 8))
    awaitClient("${block.name.string} laid", timeout = 1200) { minecraft ->
        area.any { minecraft.level!!.getBlockState(it).`is`(block) }
    }
    val pos = client { minecraft -> area.first { minecraft.level!!.getBlockState(it).`is`(block) }.immutable() }
    awaitServer("block ${pos.x} ${pos.y} ${pos.z} ${BuiltInRegistries.BLOCK.getKey(block)}")
}

private fun GameTestScope.owner() = client { UUIDUtil.uuidToIntArray(it.player!!.uuid).joinToString(",", "[I;", "]") }

private fun GameTestScope.openAnvil(pos: BlockPos) {
    awaitClient("the anvil to arrive") { it.level!!.getBlockState(pos).block is AnvilBlock }
    aim(pos)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the anvil to open") { it.gui.screen() is AnvilScreen }
}

private fun GameTestScope.pickaxes(): Int {
    val answer = query(
        "execute if items entity $playerName container.* minecraft:diamond_pickaxe",
        Regex("""Test (?:passed\. Count: (\d+)|failed)"""),
    )
    return answer.groupValues[1].toIntOrNull() ?: 0
}
