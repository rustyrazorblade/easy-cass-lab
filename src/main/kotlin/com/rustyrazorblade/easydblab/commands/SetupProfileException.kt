package com.rustyrazorblade.easydblab.commands

/**
 * Exception thrown when SetupProfile encounters a fatal error.
 * This replaces exitProcess(1) to allow testing of error paths.
 *
 * @param message Error message describing what failed
 * @param cause Optional underlying exception
 */
class SetupProfileException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
