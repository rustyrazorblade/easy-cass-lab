package com.rustyrazorblade.easycasslab.mcp

/**
 * Represents a prompt resource loaded from a markdown file.
 *
 * @property name The unique name/identifier of the prompt
 * @property description A brief description of what the prompt does
 * @property content The actual prompt content in markdown format
 */
data class PromptResource(
    val name: String,
    val description: String,
    val content: String,
)
