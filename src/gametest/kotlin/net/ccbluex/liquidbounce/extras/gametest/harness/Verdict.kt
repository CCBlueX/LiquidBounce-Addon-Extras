package net.ccbluex.liquidbounce.extras.gametest.harness

/**
 * Everything the server objected to: a Grim flag or setback, a movement Paper corrected, a kick, a quit the
 * test did not ask for, or a warning or error that names the player.
 */
class Verdict(private val server: PaperServer, private val playerName: () -> String) {

    private val excused = mutableListOf<IntRange>()
    private val excusedSetbacks = mutableListOf<IntRange>()
    private var leaving = Int.MAX_VALUE

    fun excuse(lines: IntRange) {
        excused += lines
    }

    fun excuseSetbacks(lines: IntRange) {
        excusedSetbacks += lines
    }

    /** From the given console line on, the player leaves on purpose. */
    fun leaving(since: Int) {
        leaving = since
    }

    fun violations() = server.console.withIndex()
        .filter { (index, line) -> !isExcused(line, index) && isViolation(line, index < leaving) }
        .map { it.value }

    fun isViolation(line: ConsoleLine, beforeLeaving: Boolean = true) = when (line.report) {
        ProbeReport.FLAG, ProbeReport.SETBACK, ProbeReport.FAILMOVE, ProbeReport.KICK -> true
        ProbeReport.QUIT -> beforeLeaving
        else -> (line.level == "WARN" || line.level == "ERROR") && playerName() in line.message
    }

    private fun isExcused(line: ConsoleLine, index: Int) = excused.any { index in it } ||
        line.report == ProbeReport.SETBACK && excusedSetbacks.any { index in it }

}
