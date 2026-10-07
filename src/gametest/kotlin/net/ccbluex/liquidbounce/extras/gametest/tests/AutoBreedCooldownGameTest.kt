package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoBreed
import net.minecraft.world.entity.animal.Animal

class AutoBreedCooldownGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.3 with minecraft:wheat 16")
    for (pos in listOf(origin.south(2), origin.south(2).east())) {
        summon("minecraft:cow", pos, "{Tags:['parents']}")
    }
    run("execute as @e[type=minecraft:cow] run attribute @s minecraft:movement_speed base set 0")
    awaitClient("the parents") {
        it.level!!.entitiesForRendering().count { entity -> entity is Animal } == 2
    }
    enable(ModuleAutoBreed)
    awaitClient("the first calf", timeout = 400) {
        it.level!!.entitiesForRendering().any { entity -> entity is Animal && entity.isBaby }
    }
    awaitServer("items entity $playerName container.* minecraft:wheat[count=14]")
    // Keep the calf from pushing its stationary parents out of feeding range during the five-minute wait.
    run("tp @e[type=minecraft:cow,tag=!parents] 8 -60 8")
    run("execute as @e[type=minecraft:cow,tag=!parents] run attribute @s minecraft:movement_speed base set 0")
    ticks(6000)
    awaitServer("items entity $playerName container.* minecraft:wheat[count=14]")
    travel(origin)
    awaitClient("a second calf after the real cooldown", timeout = 1000) {
        it.level!!.entitiesForRendering().count { entity -> entity is Animal && entity.isBaby } == 2
    }
    awaitAnswer("execute if entity @e[type=minecraft:cow]", "Test passed. Count: 4")
    awaitServer("items entity $playerName container.* minecraft:wheat[count=12]")
    disable(ModuleAutoBreed)
    check(client { ModuleAutoBreed.confirmed.isEmpty() })
})
