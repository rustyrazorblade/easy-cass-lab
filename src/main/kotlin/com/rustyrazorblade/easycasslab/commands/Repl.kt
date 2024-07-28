package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context

import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import java.io.IOException

class Repl(val context: Context) : ICommand {
    override fun execute() {
        try {
            // Set up the terminal
            val terminal: Terminal = TerminalBuilder.builder().build()

            // just to prove this out
            // todo: add cluster names, C* configuration options
            // also make it context aware
            var parser = CommandLineParser(context)
            val commands = parser.commands.map { it.name }

            // Set up the line reader
            val reader: LineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(StringsCompleter(commands))
                .build()

            var line: String?
            while (true) {
                // Read user input with prompt
                try {
                    // TODO enhance this with a better status line about the cluster
                    line = reader.readLine(getPrompt())
                    if (line.equals("exit", ignoreCase = true)) {
                        break
                    }

                    // blank line we can just start the loop over
                    if (line.isBlank()) {
                        continue
                    }

                    // Handle the command input
                    // we have to create a new parser every time due to a jcommander limitation
                    // See https://github.com/cbeust/jcommander/issues/271
                    parser = CommandLineParser(context)
                    parser.eval(line)
                } catch (e: UserInterruptException) {
                    // Handle CTRL+C
                    break
                } catch (e: EndOfFileException) {
                    // Handle CTRL+D
                    break
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getPrompt() : String {
        return "> "
    }


}