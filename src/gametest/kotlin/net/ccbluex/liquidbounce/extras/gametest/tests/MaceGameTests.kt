package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.teleport
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBreachSwap
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleElytraDive
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleInstantLunge
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleMaceAssist
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldDrain
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.zombie.Drowned
import net.minecraft.world.entity.monster.zombie.Husk
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.EntityHitResult
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.math.max

// A husk stands in for the enemy: hostile, so LiquidBounce's target settings pick it, and it does not burn
private const val ENEMY = "{NoAI:1b,PersistenceRequired:1b,Silent:1b," +
    "attributes:[{id:'minecraft:max_health',base:500d}],Health:500f}"
private const val FULL_HEALTH = 500f

private const val BREACH_MACE = "minecraft:mace[enchantments={'minecraft:breach':4}]"
private const val DENSITY_MACE = "minecraft:mace[enchantments={'minecraft:density':5}]"

class MaceAssistGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    hotbar(0, "minecraft:iron_sword")
    hotbar(1, BREACH_MACE)
    hotbar(2, DENSITY_MACE)
    hotbar(3, "minecraft:diamond_chestplate")
    run("item replace entity $playerName armor.chest with minecraft:elytra")
    summonEnemy(0.5, 1.5)
    client {
        ModuleMaceAssist.setting<Any>("AutoHit").setByString("Aim")
        ModuleMaceAssist.setting<Float>("MinFall").set(2f)
    }
    enable(ModuleMaceAssist)

    // Nobody below: the sword stays
    teleport(origin.west(10).above(6))
    awaitClient("the fall") { !it.player!!.onGround() }
    awaitLanding()
    awaitServer("items entity $playerName hotbar.1 minecraft:mace[damage=0]")
    check(selectedSlot() == 0) { "the slot changed without an enemy below" }
    travel(origin)

    // A short fall takes the Breach mace
    teleport(origin.above(6), pitch = 60f)
    awaitServer("items entity $playerName hotbar.1 minecraft:mace[damage=1]")
    awaitLanding()
    check(health() < FULL_HEALTH) { "the smash did not land" }
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: 0")

    // A glide straight down: the chestplate goes on before the impact, then the Density mace hits
    travel(origin)
    val before = health()
    teleport(origin.above(16), pitch = 90f)
    awaitClient("the fall") { !it.player!!.onGround() && it.player!!.fallDistance > 1.0 }
    input.holdKeyFor({ it.keyJump }, 2)
    awaitClient("the glide") { it.player!!.isFallFlying }
    awaitServer("items entity $playerName hotbar.2 minecraft:mace[damage=1]")
    awaitServer("items entity $playerName armor.chest minecraft:diamond_chestplate")
    awaitServer("items entity $playerName hotbar.3 minecraft:elytra")
    awaitLanding()
    check(health() < before) { "the dive did not land" }
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: 0")

    // Aim leaves an enemy behind a cobweb alone; the fall reports the chance a stun would get on the way
    travel(origin)
    val web = origin.south().above()
    setBlock(web, "minecraft:cobweb")
    awaitClient("the cobweb") { it.level!!.getBlockState(web).`is`(Blocks.COBWEB) }
    client {
        ModuleMaceAssist.StunSlam.setting<Int>("Chance").set(100)
        ModuleMaceAssist.StunSlam.setting<Boolean>("ScaleWithFall").set(true)
    }
    val unhurt = health()
    teleport(origin.above(6), pitch = 60f)
    awaitClient("a chance that grows with the fall") {
        val fallen = it.player!!.fallDistance
        fallen > 1.0 && abs(ModuleMaceAssist.StunSlam.chanceNow - fallen.toFloat() / 8f * 100f) < 0.01f
    }
    awaitLanding()
    ticks(5)
    check(health() == unhurt) { "the mace swung through the cobweb" }
    run("kill @e[type=minecraft:husk]")
    setBlock(web, "minecraft:air")
    client { ModuleMaceAssist.StunSlam.setting<Boolean>("ScaleWithFall").set(false) }

    // StunSlam against a drowned with a raised trident. Paper lets the trident take no hit, but neither does it wear
    // the weapon then; the stun shows in the slot sent before the hit and in what the smash still deals.
    hotbar(4, IRON_AXE)
    summonBlocker(0.5, 1.5)

    // Chance, and the chance is nil: the mace smashes without the axe first
    client {
        ModuleMaceAssist.StunSlam.setting<Any>("Trigger").setByString("Chance")
        ModuleMaceAssist.StunSlam.setting<Int>("Chance").set(0)
    }
    var health = health("drowned")
    check(slam() == 0) { "the axe came out at a chance of 0" }
    check(health - health("drowned") > SMASH) { "the mace did not smash" }

    // Key, held, while ShieldDrain drains the same trident: the stun is left to it
    client {
        ModuleMaceAssist.StunSlam.setting<Any>("Trigger").setByString("Key")
        ModuleMaceAssist.StunSlam.setting<InputConstants.Key>("Key")
            .set(InputConstants.Type.KEYBOARD.getOrCreate(STUN_KEY))
        ModuleShieldDrain.setting<Boolean>("CrosshairOnly").set(false)
    }
    input.holdKey(STUN_KEY)
    enable(ModuleShieldDrain)
    awaitClient("the drain") { ModuleShieldDrain.draining }
    check(slam() == 0) { "the axe came out while ShieldDrain drained" }
    disable(ModuleShieldDrain)

    // Key, held: the axe goes first, every time the mace would hit the raised trident, so no smash gets through
    awaitClient("the trident to be raised again", condition = raisedTrident)
    ticks(20)
    health = health("drowned")
    check(slam() > 0) { "no axe with the key held" }
    check(health - health("drowned") in 0f..SMASH) { "the mace smashed into the raised trident" }

    // A cooldown lets the mace follow the axe, and keeps the next fall from another stun
    client { ModuleMaceAssist.StunSlam.setting<Int>("Cooldown").set(5000) }
    ticks(20)
    health = health("drowned")
    check(slam() > 0) { "no axe before the cooldown" }
    check(health - health("drowned") > SMASH) { "the mace did not follow the axe" }
    check(slam() == 0) { "a second stun within the cooldown" }

    // AxeHold: the axe stays selected for its ticks before it swings
    ticks(100)
    client { ModuleMaceAssist.StunSlam.setting<Int>("AxeHold").set(2) }
    val held = slam()
    check(held >= 2) { "the axe was held for $held tick(s) only" }
    input.releaseKey(STUN_KEY)
    client {
        ModuleMaceAssist.StunSlam.setting<Any>("Trigger").setByString("Always")
        ModuleMaceAssist.StunSlam.setting<Int>("AxeHold").set(0)
        ModuleMaceAssist.StunSlam.setting<Int>("Cooldown").set(0)
    }

    disable(ModuleMaceAssist)
    check(client { ModuleMaceAssist.target == null && !SilentHotbar.isSlotModifiedBy(ModuleMaceAssist) }) {
        "disabling left a target or a held mace"
    }
})

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

class ElytraDiveGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    run("item replace entity $playerName armor.chest with minecraft:diamond_chestplate")
    // Neither on the hotbar: both come from the inventory, the elytra first, into the first empty slots
    run("item replace entity $playerName container.20 with minecraft:elytra")
    run("item replace entity $playerName container.21 with minecraft:firework_rocket 16")
    awaitClient("the elytra and the rockets") { it.player!!.inventory.getItem(21).count == 16 }
    travel(origin, pitch = -30f)

    // The macro: elytra on, jump, glide, rocket, and the chestplate back after two seconds
    client { ModuleElytraDive.setting<Int>("FlightTime").set(2) }
    enable(ModuleElytraDive)
    awaitServer("items entity $playerName armor.chest minecraft:elytra")
    awaitServer("entity @a[name=$playerName,nbt={FallFlying:1b}]")
    awaitServer("items entity $playerName hotbar.1 minecraft:firework_rocket[count=15]")
    awaitClient("the glide") { it.player!!.isFallFlying }
    input.holdKeyFor({ it.keyJump }, 3)
    awaitServer("items entity $playerName hotbar.1 minecraft:firework_rocket[count=14]")
    screenshot("ElytraDive")
    awaitServer("items entity $playerName armor.chest minecraft:diamond_chestplate", timeout = 100)
    // Pulled to the first slot, the elytra stays there once swapped back out
    awaitServer("items entity $playerName hotbar.0 minecraft:elytra")
    awaitLanding()
    disable(ModuleElytraDive)
    check(client { ModuleElytraDive.steerAt == null }) { "disabling left a steering point" }

    // RequireLand: enabled in the air, it takes off only once it stood on the ground
    travel(origin, pitch = -30f)
    teleport(origin.above(4), pitch = -30f)
    awaitClient("the fall") { !it.player!!.onGround() }
    enable(ModuleElytraDive)
    awaitClient("the landing", timeout = 60) {
        val player = it.player!!
        check(player.onGround() || !player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)) {
            "took off in the air"
        }
        player.onGround()
    }
    awaitServer("items entity $playerName armor.chest minecraft:elytra")
    awaitServer("items entity $playerName hotbar.1 minecraft:firework_rocket[count=13]")
    awaitServer("items entity $playerName armor.chest minecraft:diamond_chestplate", timeout = 100)
    awaitLanding()
    disable(ModuleElytraDive)

    // A chestplate only in the inventory goes back on once landed; looking down, the glide ends soon
    travel(origin, pitch = 10f)
    run("item replace entity $playerName armor.chest with minecraft:elytra")
    run("item replace entity $playerName hotbar.0 with minecraft:air")
    run("item replace entity $playerName container.22 with minecraft:diamond_chestplate")
    awaitClient("the chestplate") { it.player!!.inventory.getItem(22).`is`(Items.DIAMOND_CHESTPLATE) }
    client { ModuleElytraDive.setting<Int>("FlightTime").set(1) }
    enable(ModuleElytraDive)
    awaitServer("entity @a[name=$playerName,nbt={FallFlying:1b}]")
    awaitLanding()
    awaitServer("items entity $playerName armor.chest minecraft:diamond_chestplate", timeout = 100)
    awaitServer("items entity $playerName hotbar.0 minecraft:elytra")
    disable(ModuleElytraDive)

    // ReDive: after the first smash it takes off, climbs above the husk and dives onto it again
    travel(origin)
    hotbar(0, "minecraft:iron_sword")
    hotbar(1, "minecraft:elytra")
    hotbar(2, "minecraft:firework_rocket 16")
    hotbar(3, DENSITY_MACE)
    summonEnemy(0.5, 1.5)
    client {
        ModuleMaceAssist.setting<Any>("AutoHit").setByString("Aim")
        ModuleMaceAssist.setting<Float>("MinFall").set(2f)
        ModuleElytraDive.ReDive.setting<Boolean>("Enabled").set(true)
        ModuleElytraDive.ReDive.setting<Float>("Height").set(10f)
    }
    enable(ModuleMaceAssist)
    teleport(origin.above(7), pitch = 60f)
    awaitClient("the fall") { !it.player!!.onGround() }
    enable(ModuleElytraDive)
    // The husk stands on the ground: after the first smash a second one needs another fall from above it
    awaitClient("the climb above the husk") { it.player!!.isFallFlying && it.player!!.y > origin.y + 8 }
    awaitServer("items entity $playerName hotbar.3 minecraft:mace[damage=2]", timeout = 400)
    disable(ModuleElytraDive)
    disable(ModuleMaceAssist)
    awaitLanding()
})

class ShieldDrainGameTest : PaperGameTest({
    hotbar(0, "minecraft:iron_sword")
    hotbar(1, BREACH_MACE)
    run("item replace entity $playerName weapon.offhand with minecraft:shield")
    summonEnemy(0.5, 2.5)
    aim(origin.south(2).above())

    // Without a raised shield there is nothing to drain
    enable(ModuleShieldDrain)
    ticks(40)
    check(client { ModuleShieldDrain.target == null }) { "a husk without a shield became a target" }
    check(health() == FULL_HEALTH) { "the husk was hit" }
    run("kill @e[type=minecraft:husk]")

    // A raised trident, held back by one gate after another
    summonBlocker(0.5, 2.5)
    client { ModuleShieldDrain.setting<Float>("MinFall").set(2f) }
    heldBack("before a fall")
    client {
        ModuleShieldDrain.setting<Float>("MinFall").set(0f)
        ModuleShieldDrain.setting<Boolean>("PauseOnGround").set(true)
    }
    heldBack("on the ground")
    client {
        ModuleShieldDrain.setting<Boolean>("PauseOnGround").set(false)
        ModuleShieldDrain.setting<Boolean>("CrosshairOnly").set(true)
    }
    aim(origin.east(3).above())
    heldBack("off the crosshair")
    client { ModuleShieldDrain.setting<Boolean>("CrosshairOnly").set(false) }
    input.pressKey { it.keyInventory }
    awaitClient("the inventory") { it.gui.screen() != null }
    heldBack("in a screen")
    input.pressKey(InputConstants.KEY_ESCAPE)
    awaitClient("the inventory to close") { it.gui.screen() == null }
    input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the own shield up") { it.player!!.isUsingItem }
    heldBack("while blocking")
    input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the own shield down") { !it.player!!.isUsingItem }

    // On the crosshair and nothing else in the way: the mace hits the drowned
    client { ModuleShieldDrain.setting<Boolean>("CrosshairOnly").set(true) }
    enable(ModuleShieldDrain)
    aim(origin.south(2).above())
    awaitClient("the drowned under the crosshair and its trident up") { minecraft ->
        raisedTrident(minecraft) && (minecraft.hitResult as? EntityHitResult)?.entity is Drowned
    }
    awaitClient("the drain") { ModuleShieldDrain.draining }
    awaitClient("a swing at the drowned", timeout = 60) { it.player!!.isSwinging }
    awaitServer("entity @e[type=minecraft:drowned,nbt=!{Health:500.0f}]")

    disable(ModuleShieldDrain)
    check(client { !ModuleShieldDrain.draining && !SilentHotbar.isSlotModifiedBy(ModuleShieldDrain) }) {
        "disabling left a target or the mace selected"
    }
})

class InstantLungeGameTest : PaperGameTest({
    hotbar(0, "minecraft:iron_sword")
    hotbar(2, "minecraft:iron_spear[enchantments={'minecraft:lunge':3}]")
    awaitClient("the spear") { it.player!!.inventory.getItem(2).`is`(Items.IRON_SPEAR) }
    ticks(20)

    enable(ModuleInstantLunge)
    awaitClient("the module to turn itself off") { !ModuleInstantLunge.enabled }
    check(!client { it.chatContains("lunge") || it.chatContains("Lunge") }) { "the module refused to lunge" }
    awaitServer("items entity $playerName hotbar.2 minecraft:iron_spear[damage=1]")
    val z = query("data get entity $playerName Pos[2]", Regex("entity data: ([-\\d.]+)d")).groupValues[1].toDouble()
    check(z > 1.5) { "the server did not see the lunge, z is $z" }
    awaitAnswer("data get entity $playerName SelectedItemSlot", "entity data: 0")
    screenshot("InstantLunge")

    // No Lunge spear: a note, nothing else
    run("clear $playerName minecraft:iron_spear")
    awaitClient("the spear to go") { !it.player!!.inventory.getItem(2).`is`(Items.IRON_SPEAR) }
    enable(ModuleInstantLunge)
    awaitClient("the note") { it.chatContains("No spear with Lunge") && !ModuleInstantLunge.enabled }
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

private fun GameTestScope.awaitLanding() = awaitClient("the landing", timeout = 400) { it.player!!.onGround() }

private fun GameTestScope.health(type: String = "husk") =
    query("data get entity @e[type=minecraft:$type,limit=1] Health", Regex("entity data: ([\\d.]+)f"))
        .groupValues[1].toFloat()

private fun GameTestScope.selectedSlot() =
    query("data get entity $playerName SelectedItemSlot", Regex("entity data: (\\d+)")).groupValues[1].toInt()

// A drowned keeps its trident raised for as long as it has a target, facing north at you. Paper lets no mob block,
// so every hit lands and wears the weapon; the client still sees a raised shield and acts on it.
private const val BLOCKER = "{PersistenceRequired:1b,Silent:1b,Health:500f,Rotation:[180f,0f],attributes:[" +
    "{id:'minecraft:max_health',base:500d},{id:'minecraft:movement_speed',base:0d}," +
    "{id:'minecraft:knockback_resistance',base:1d}],equipment:{mainhand:{id:'minecraft:trident',count:1," +
    "components:{'minecraft:blocks_attacks':{}}}}}"

private const val IRON_AXE = "minecraft:iron_axe"

// Less than a six block smash deals to the drowned
private const val SMASH = 8f
private const val STUN_KEY = InputConstants.KEY_NUMPAD9

private fun GameTestScope.summonBlocker(x: Double, z: Double) {
    run("difficulty easy")
    run("time set midnight")
    // Its tridents neither hurt nor push you
    run("effect give $playerName minecraft:resistance infinite 4 true")
    run("attribute $playerName minecraft:knockback_resistance base set 1")
    run("summon minecraft:drowned $x ${origin.y} $z $BLOCKER")
    awaitClient("the raised trident", timeout = 200, condition = raisedTrident)
}

private val raisedTrident = { minecraft: Minecraft ->
    minecraft.level!!.entitiesForRendering().filterIsInstance<Drowned>().any { it.isBlocking }
}

// Twenty ticks with a raised trident in reach and nothing drained
private fun GameTestScope.heldBack(why: String) {
    enable(ModuleShieldDrain)
    ticks(20)
    check(client { ModuleShieldDrain.target == null }) { "a target $why" }
    check(client(raisedTrident)) { "the trident went down $why" }
    disable(ModuleShieldDrain)
    awaitServer("items entity $playerName hotbar.1 minecraft:mace[damage=0]")
    check(health("drowned") == FULL_HEALTH) { "drained $why" }
}

/** Falls onto the drowned and returns the longest run of ticks the axe was the slot sent to the server. */
private fun GameTestScope.slam(): Int {
    var run = 0
    var longest = 0
    teleport(origin.above(6), pitch = 60f)
    awaitClient("the fall") { !it.player!!.onGround() }
    var waited = 0
    var landed = 0
    // Ten more ticks after the landing: a held axe may still swing
    while (landed < 10 && waited++ < 400) {
        run = if (client { SilentHotbar.serversideSlot } == 4) run + 1 else 0
        longest = max(longest, run)
        if (client { it.player!!.onGround() }) landed++
        ticks(1)
    }
    return longest
}
