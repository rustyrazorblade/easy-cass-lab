package com.rustyrazorblade.easydblab.annotations

/**
 * Annotation to mark commands that require Docker to be available.
 * Commands annotated with this will have Docker availability checked
 * before execution.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireDocker
