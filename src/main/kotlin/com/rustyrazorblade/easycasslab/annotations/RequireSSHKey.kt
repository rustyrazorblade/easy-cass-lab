package com.rustyrazorblade.easycasslab.annotations

/**
 * Annotation to mark commands that require an SSH key to be present.
 * Commands with this annotation will have their SSH key validated before execution.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireSSHKey