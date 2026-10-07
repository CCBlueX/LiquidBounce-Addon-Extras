package net.ccbluex.liquidbounce.extras.gametest.harness

/**
 * Everything the server objected to: a Grim flag or setback, a movement Paper corrected, a kick, a quit the
 * test did not ask for, or a warning or error that names the player.
 */
class Verdict(private val server: PaperServer, private val playerName: () -> String) {

    private val excused = mutableListOf<IntRange>()
    private val excusedSetbacks = mutableListOf<IntRange>()
    private val departures = mutableListOf<IntRange>()

    fun excuse(lines: IntRange) {
        excused += lines
    }

    fun excuseSetbacks(lines: IntRange) {
        excusedSetbacks += lines
    }

    /** Between these console lines, the player left on purpose. */
    fun departed(lines: IntRange) {
        departures += lines
    }

    fun violations() = server.console.withIndex()
        .filter { (index, line) -> !isExcused(line, index) && isViolation(line, departures.any { index in it }) }
        .map { it.value }

    fun isViolation(line: ConsoleLine, leaving: Boolean = false) = when (line.report) {
        ProbeReport.FLAG, ProbeReport.SETBACK, ProbeReport.FAILMOVE, ProbeReport.KICK -> true
        ProbeReport.QUIT -> !leaving
        else -> (line.level == "WARN" || line.level == "ERROR") && playerName() in line.message
    }

    private fun isExcused(line: ConsoleLine, index: Int) = excused.any { index in it } ||
        line.report == ProbeReport.SETBACK && excusedSetbacks.any { index in it }

}
