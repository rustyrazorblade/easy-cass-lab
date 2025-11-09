package com.rustyrazorblade.easycasslab.configuration

/**
 * Annotation for marking User data class fields that require interactive configuration.
 * Provides metadata for prompting users for field values in ordered sequence.
 *
 * @param order The order in which fields should be prompted (ascending)
 * @param prompt The question text to display to the user
 * @param default The default value if user provides empty input
 * @param secret Whether to hide user input (for passwords/keys)
 * @param skippable Whether to skip user prompting (for programmatically set fields)
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigField(
    val order: Int,
    val prompt: String,
    val default: String = "",
    val secret: Boolean = false,
    val skippable: Boolean = false,
)
