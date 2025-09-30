package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.IOException

class Repl(context: Context) : BaseCommand(context) {
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

    private fun createTerminal(): Terminal {
        return TerminalBuilder.builder().build()
    }

    private fun createLineReader(terminal: Terminal): LineReader {
        // just to prove this out
        // todo: add cluster names, C* configuration options
        // also make it context aware
        val parser = CommandLineParser(context)
        val commands = parser.commands.map { it.name }

        return LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(StringsCompleter(commands))
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

    private fun shouldExit(line: String): Boolean {
        return line.equals("exit", ignoreCase = true)
    }

    private fun processCommand(line: String) {
        // we have to create a new parser every time due to a jcommander limitation
        // See https://github.com/cbeust/jcommander/issues/271
        val parser = CommandLineParser(context)
        parser.eval(line)
    }

    private val prompt = "> "
}
