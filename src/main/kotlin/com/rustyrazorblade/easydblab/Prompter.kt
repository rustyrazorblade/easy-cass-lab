package com.rustyrazorblade.easydblab

/**
 * Interface for user prompting, enabling testability through dependency injection.
 * Production code uses ConsolePrompter, while tests can inject a mock implementation.
 */
interface Prompter {
    /**
     * Prompts the user for input.
     *
     * @param question The question to display
     * @param default The default value if user provides empty input
     * @param secret If true, input should be hidden (for passwords)
     * @return The user's response, or default if empty
     */
    fun prompt(
        question: String,
        default: String,
        secret: Boolean = false,
    ): String
}

/**
 * Console-based implementation of Prompter for production use.
 * Delegates to Utils.prompt() for actual console I/O.
 */
class ConsolePrompter : Prompter {
    override fun prompt(
        question: String,
        default: String,
        secret: Boolean,
    ): String = Utils.prompt(question, default, secret)
}
