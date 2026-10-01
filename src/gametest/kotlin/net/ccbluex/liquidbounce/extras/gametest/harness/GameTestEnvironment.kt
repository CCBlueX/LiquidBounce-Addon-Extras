package net.ccbluex.liquidbounce.extras.gametest.harness

import java.nio.file.Path
import kotlin.io.path.Path

/** What `build.gradle.kts` hands the client game test run as system properties. */
object GameTestEnvironment {

    val paperDirectory = Path(property("extras.paper.directory"))
    val probe = Path(property("extras.paper.probe"))
    private val minecraft = property("extras.paper.minecraft")

    private val selection = System.getProperty("extras.gametest.only")
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()

    val distribution by lazy {
        val downloads = PaperDistribution(paperDirectory.resolve("cache"))
        Distribution(
            paper = downloads.paper(minecraft, System.getProperty("extras.paper.build")),
            grim = downloads.grim(System.getProperty("extras.grim.version")),
        )
    }

    fun isSelected(test: String) = selection == null || test in selection

    private fun property(name: String) = requireNotNull(System.getProperty(name)) {
        "$name is not set, run the game tests through ./gradlew runClientGameTest"
    }

}

@JvmRecord
data class Distribution(val paper: Path, val grim: Path)
