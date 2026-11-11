package net.ccbluex.liquidbounce.extras.gametest.harness

import net.ccbluex.gametest.harness.GameTestSetup
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.features.global.GlobalSettingsClientChat
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * Puts back what the add-on keeps between tests: its module settings are saved with the client's config, and every
 * world starts unknown. The client's chat with real players stays off, their messages would end up in screenshots.
 */
object ExtrasSetup : GameTestSetup {

    override fun beforeTest(context: ClientGameTestContext) {
        // The runner resets window and options before every test. The README's screenshots need room; other runs
        // keep the small window, since the client ticks once per frame and llvmpipe draws a large one too slowly to
        // keep up with the server
        if (System.getProperty("extras.showcase") != null) {
            context.input.resizeWindow(1280, 720)
        }
        context.runOnClient<RuntimeException> { minecraft ->
            minecraft.options.guiScale().set(2)
            ModuleManager.filter { it.category in ExtrasCategories.all }.forEach { it.restore() }
            WorldJournal.folder.deleteRecursively()
            GlobalSettingsClientChat.enabled = false
        }
        ToggleToasts.reset()
    }

}
