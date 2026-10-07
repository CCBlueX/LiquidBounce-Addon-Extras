package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoBreed
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.item.Items

class AutoBreedSafetyGameTest : PaperGameTest({
    run("item replace entity $playerName weapon.offhand with minecraft:wheat 16")
    for (pos in listOf(origin.south(2), origin.south(2).east())) {
        summon("minecraft:cow", pos, "{NoAI:1b,Age:-24000}")
    }
    awaitClient("baby cows") { minecraft ->
        minecraft.level!!.entitiesForRendering().count { it is Animal && it.isBaby } == 2
    }
    enable(ModuleAutoBreed)
    ticks(60)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=16]")
    check(client { ModuleAutoBreed.confirmed.isEmpty() })
    run("execute as @e[type=minecraft:cow] run data merge entity @s {Age:0}")
    fill(origin.offset(-1, 0, 1), origin.offset(2, 2, 1), "minecraft:stone")
    ticks(60)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=16]")
    fill(origin.offset(-1, 0, 1), origin.offset(2, 2, 1), "minecraft:air")
    awaitClient("offhand feeding accepted") { ModuleAutoBreed.confirmed.size == 2 }
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=14]")
    ticks(140)
    awaitServer("items entity $playerName weapon.offhand minecraft:wheat[count=14]")
    reconnect()
    check(client { ModuleAutoBreed.confirmed.isEmpty() })
    disable(ModuleAutoBreed)
    check(client { ModuleAutoBreed.confirmed.isEmpty() })
    run("kill @e[type=minecraft:cow]")
    awaitClient("the cows to disappear") { minecraft ->
        minecraft.level!!.entitiesForRendering().none { it is Animal }
    }

    val uuid = client { net.minecraft.core.UUIDUtil.uuidToIntArray(it.player!!.uuid).joinToString(",", "[I;", "]") }
    for (details in listOf("", "Owner:$uuid,Sitting:1b", "Owner:$uuid,Health:1f")) {
        run("kill @e[type=minecraft:wolf]")
        run("item replace entity $playerName weapon.offhand with minecraft:beef 16")
        for (pos in listOf(origin.south(2), origin.south(2).east())) {
            summon("minecraft:wolf", pos, "{NoAI:1b${if (details.isEmpty()) "" else ",$details"}}")
        }
        awaitClient("the food") { it.player!!.offhandItem.`is`(Items.BEEF) }
        enable(ModuleAutoBreed)
        ticks(60)
        awaitServer("items entity $playerName weapon.offhand minecraft:beef[count=16]")
        check(client { ModuleAutoBreed.confirmed.isEmpty() })
        disable(ModuleAutoBreed)
    }
})
