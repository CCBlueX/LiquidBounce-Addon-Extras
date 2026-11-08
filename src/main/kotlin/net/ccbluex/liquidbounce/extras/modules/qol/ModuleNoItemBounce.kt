package net.ccbluex.liquidbounce.extras.modules.qol

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule

/** Hotbar stacks no longer bounce when they change. The hotbar asks [popTime] through `MixinHud`. */
object ModuleNoItemBounce : ClientModule("NoItemBounce", ExtrasCategories.QOL) {

    /** Frames that would have drawn a bouncing stack. */
    var flattenedFrames = 0
        private set

    fun popTime(popTime: Int): Int {
        if (!running || popTime <= 0) {
            return popTime
        }
        flattenedFrames++
        return 0
    }

    override fun onDisabled() {
        flattenedFrames = 0
    }

}
