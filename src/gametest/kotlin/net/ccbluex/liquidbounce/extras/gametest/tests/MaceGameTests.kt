package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBreachSwap
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.monster.zombie.Husk
import com.mojang.blaze3d.platform.InputConstants

// A husk stands in for the enemy: hostile, so LiquidBounce's target settings pick it, and it does not burn
private const val ENEMY = "{NoAI:1b,PersistenceRequired:1b,Silent:1b," +
    "attributes:[{id:'minecraft:max_health',base:500d}],Health:500f}"

private const val BREACH_MACE = "minecraft:mace[enchantments={'minecraft:breach':4}]"

class BreachSwapGameTest : PaperGameTest({
    hotbar(0, "minecraft:iron_sword")
    hotbar(1, BREACH_MACE)
    summonEnemy(0.5, 2.5)
    aim(origin.south(2).above())

    // Left to chance, and the chance is nil
    client { ModuleBreachSwap.setting<Int>("Chance").set(0) }
    enable(ModuleBreachSwap)
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("items entity $playerName hotbar.0 minecraft:iron_sword[damage=1]")
    awaitServer("items entity $playerName hotbar.1 minecraft:mace[damage=0]")
    ticks(20)

    client { ModuleBreachSwap.setting<Int>("Chance").set(100) }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("items entity $playerName hotbar.1 minecraft:mace[damage=1]")
    awaitServer("items entity $playerName hotbar.0 minecraft:iron_sword[damage=1]")
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: 0")

    disable(ModuleBreachSwap)
    check(client { !SilentHotbar.isSlotModifiedBy(ModuleBreachSwap) }) { "disabling left the mace selected" }
})

private fun GameTestScope.hotbar(slot: Int, item: String) =
    run("item replace entity $playerName hotbar.$slot with $item")

private fun husks(minecraft: Minecraft) = minecraft.level!!.entitiesForRendering().filterIsInstance<Husk>()

// Monsters need a difficulty above peaceful
private fun GameTestScope.summonEnemy(x: Double, z: Double) {
    run("difficulty easy")
    run("summon minecraft:husk $x ${origin.y} $z $ENEMY")
    awaitClient("the husk") { minecraft -> husks(minecraft).any { it.z == z } }
}
