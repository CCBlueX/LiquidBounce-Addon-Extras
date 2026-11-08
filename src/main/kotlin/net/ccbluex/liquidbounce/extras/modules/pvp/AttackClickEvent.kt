package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.CancellableEvent
import net.minecraft.world.phys.HitResult

/**
 * A click of the attack key that vanilla is about to act on: hit the entity, start breaking the block or swing
 * at the air. Cancelling it swallows the click, swing included.
 */
class AttackClickEvent(val hitResult: HitResult) : CancellableEvent()
