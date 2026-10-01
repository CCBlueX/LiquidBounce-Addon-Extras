package net.ccbluex.liquidbounce.extras.gametest.harness

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager

/**
 * One game test: a fresh Paper server with Grim, a client that joins it, [body], and a verdict on
 * everything the server objected to. The class name without `GameTest` is what `-Pgametest.only` selects.
 */
abstract class PaperGameTest(private val body: GameTestScope.() -> Unit) : FabricClientGameTest {

    private val name = javaClass.simpleName.removeSuffix("GameTest")

    final override fun runTest(context: ClientGameTestContext) {
        if (!GameTestEnvironment.isSelected(name)) {
            return
        }

        awaitClient(context)
        // Only the HUD stays on. AutoConfig, on by default, would load a marketplace config on join and
        // switch built-in modules on in the middle of the test.
        context.runOnClient<RuntimeException> {
            ModuleManager.filter { it.enabled && it !== ModuleHud }.forEach { it.enabled = false }
        }
        ClientErrors.reset()
        try {
            PaperServer.start(name).use { server ->
                val scope = GameTestScope(context, server)
                scope.use {
                    it.awaitReady()
                    it.join()
                    it.body()
                }
                val violations = scope.verdict.violations()
                check(violations.isEmpty()) {
                    "the server objected ${violations.size} time(s), see ${server.log}\n" +
                        violations.joinToString("\n") { "  $it" }
                }
            }
            ClientErrors.assertNone()
            GameTestResults.passed(name)
        } catch (failure: Throwable) {
            GameTestResults.failed(name, failure)
        }
    }

    private fun awaitClient(context: ClientGameTestContext) {
        // LiquidBounce holds a progress screen until its startup tasks are done, and aborting the browser's
        // first page load by joining a server is fatal to it
        context.waitFor({ it.gui.screen() is TitleScreen }, STARTUP_TIMEOUT)
        context.waitFor({ LiquidBounce.isInitialized && ScreenManager.mainBrowser != null }, STARTUP_TIMEOUT)
    }

    private companion object {
        const val STARTUP_TIMEOUT = 20 * 60 * 5
    }

}

/** Collects every test's outcome so one failure does not hide the others; [ResultsGameTest] reports them. */
object GameTestResults {

    private val logger = LogManager.getLogger("Extras/GameTest")
    private val failures = linkedMapOf<String, Throwable>()
    private var passed = 0

    fun passed(test: String) {
        passed++
        logger.info("{} passed", test)
    }

    fun failed(test: String, failure: Throwable) {
        failures[test] = failure
        logger.warn("{} failed", test, failure)
    }

    fun assertAllPassed() {
        logger.info("{} game test(s) passed, {} failed", passed, failures.size)
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.entries.joinToString("\n") { (test, failure) -> "$test: ${failure.message}" })
                .apply { failures.values.forEach(::addSuppressed) }
        }
    }

}

class ResultsGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = GameTestResults.assertAllPassed()
}
