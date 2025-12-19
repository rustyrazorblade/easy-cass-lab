package com.rustyrazorblade.easydblab

/**
 * Test implementation of Prompter that returns predefined responses.
 * Useful for testing commands that require user input without actual console interaction.
 *
 * Responses can be looked up by:
 * 1. Exact question match
 * 2. Question contains key (for partial matching)
 *
 * Also tracks all prompts that were called for verification in tests.
 *
 * Supports sequential responses for retry testing - use addSequentialResponse() to configure
 * different responses for the same question on subsequent calls.
 *
 * @param responses Map of question text (or partial match) to response value
 */
class TestPrompter(
    private val responses: Map<String, String> = emptyMap(),
) : Prompter {
    private val callLog = mutableListOf<PromptCall>()
    private val sequentialResponses = mutableMapOf<String, MutableList<String>>()
    private val sequentialCallCounts = mutableMapOf<String, Int>()

    /**
     * Records details about each prompt call for test verification.
     */
    data class PromptCall(
        val question: String,
        val default: String,
        val secret: Boolean,
        val returnedValue: String,
    )

    /**
     * Adds sequential responses for a question key.
     * Each call to prompt() with a matching question will return the next response in the list.
     * After all responses are exhausted, returns the last response repeatedly.
     *
     * @param key The key to match (question contains this key)
     * @param responses The responses to return in order
     */
    fun addSequentialResponses(
        key: String,
        vararg responses: String,
    ) {
        sequentialResponses[key] = responses.toMutableList()
        sequentialCallCounts[key] = 0
    }

    override fun prompt(
        question: String,
        default: String,
        secret: Boolean,
    ): String {
        // Check for sequential responses first
        val sequentialResponse = getSequentialResponse(question)
        if (sequentialResponse != null) {
            callLog.add(PromptCall(question, default, secret, sequentialResponse))
            return sequentialResponse
        }

        // Try exact match first
        val response =
            responses[question]
                // Then try partial match (question contains key)
                ?: responses.entries.find { question.contains(it.key, ignoreCase = true) }?.value
                // Fall back to default
                ?: default

        callLog.add(PromptCall(question, default, secret, response))
        return response
    }

    /**
     * Gets the next sequential response for a matching question.
     */
    private fun getSequentialResponse(question: String): String? {
        for ((key, responses) in sequentialResponses) {
            if (question.contains(key, ignoreCase = true)) {
                val callCount = sequentialCallCounts[key] ?: 0
                sequentialCallCounts[key] = callCount + 1

                // Return the response at the current call count, or the last one if exhausted
                val index = minOf(callCount, responses.size - 1)
                return responses[index]
            }
        }
        return null
    }

    /**
     * Returns all prompt calls made during the test.
     */
    fun getCallLog(): List<PromptCall> = callLog.toList()

    /**
     * Returns true if any prompt was called with a question containing the given text.
     */
    fun wasPromptedFor(questionContains: String): Boolean = callLog.any { it.question.contains(questionContains, ignoreCase = true) }

    /**
     * Clears the call log and sequential response state. Useful when reusing a TestPrompter across multiple test cases.
     */
    fun clear() {
        callLog.clear()
        sequentialResponses.clear()
        sequentialCallCounts.clear()
    }
}
