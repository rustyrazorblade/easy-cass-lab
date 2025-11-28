package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import picocli.CommandLine.Command
import java.io.IOException

/**
 * Starts an interactive REPL for executing commands.
 */
@RequireProfileSetup
@Command(
    name = "repl",
    description = ["Start interactive REPL"],
)
class Repl(
    context: Context,
) : PicoBaseCommand(context) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun execute() {
        try {
            val terminal = createTerminal()
            val reader = createLineReader(terminal)
            runReplLoop(reader)
        } catch (e: IOException) {
            log.error(e) { "IO error in REPL terminal setup" }
        }
    }

    private fun createTerminal(): Terminal = TerminalBuilder.builder().build()

    private fun createLineReader(terminal: Terminal): LineReader {
        // Build command list for completion
        val parser = CommandLineParser(context)
        val allCommands = parser.picoCommands.flatMap { listOf(it.name) + it.aliases }.distinct()

        return LineReaderBuilder
            .builder()
            .terminal(terminal)
            .completer(StringsCompleter(allCommands))
            .build()
    }

    private fun runReplLoop(reader: LineReader) {
        var shouldContinue = true
        while (shouldContinue) {
            try {
                val line = readUserInput(reader)
                if (line == null || shouldExit(line)) {
                    shouldContinue = false
                } else if (line.isNotBlank()) {
                    processCommand(line)
                }
            } catch (ignored: UserInterruptException) {
                // Handle CTRL+C
                shouldContinue = false
            } catch (ignored: EndOfFileException) {
                // Handle CTRL+D
                shouldContinue = false
            }
        }
    }

    private fun readUserInput(reader: LineReader): String? {
        // TODO enhance this with a better status line about the cluster
        return reader.readLine(prompt)
    }

    private fun shouldExit(line: String): Boolean = line.equals("exit", ignoreCase = true)

    private fun processCommand(line: String) {
        // Create a new parser for each command to ensure fresh command instances
        val parser = CommandLineParser(context)
        parser.eval(line)
    }

    private val prompt = "> "
}
